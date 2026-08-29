---
name: release
description: Cut a code-karta release to Maven Central. Use when asked to release, publish, cut a version, or tag a new code-karta version. Prepares everything, then stops at the one irreversible step for a human to approve.
---

# Releasing code-karta

Publishing to Maven Central is **irreversible** — a version, once published, can never be
replaced or withdrawn, only superseded. So this skill does all the work up front and stops
before the push that triggers it.

## How the release actually happens

`.github/workflows/publish.yml` runs `mvn clean deploy -P release` on any pushed tag
matching `v*`. There is no manual deploy step and no local credential: **pushing the tag is
the release**. Everything before that is reversible; the tag push is not.

## Steps

### 1. Establish what is being released

```bash
git fetch --tags
git tag --sort=-v:refname | head -3          # the last released version
git log --oneline $(git describe --tags --abbrev=0)..main
```

Read the commits, not just their subjects. The version number follows from what they
actually changed:

- new flags, new parsers, new diagram kinds, behaviour that consumers can now rely on → **minor**
- fixes only → **patch**
- a changed public signature, a removed flag, a changed output filename → **major** (and
  check `@AIContract` in `KartaCli` first — those signatures are a published contract)

A change to the emitted SVG bytes is a consumer-visible change even when no API moved:
downstream repos commit these files and will see a diff.

### 2. Confirm main is releasable

```bash
mvn -B clean verify
```

This is the real gate — it runs the tests plus Checkstyle, PMD, and SpotBugs, and CI runs
the same thing. `mvn test` alone is not sufficient: SpotBugs has failed the build on changes
that passed every test, and `test` never reaches the Maven plugin's integration tests, which
run the goal from real Maven builds and are the only thing that exercises how a user's pom
binds to it. Those need network on a cold local repository; they are slow, not optional.

Also confirm the Gradle build still compiles if it was touched:

```bash
./gradlew test
```

### 3. Set the version

The Maven version is the release version — there is no separate SNAPSHOT-to-release step in
this repo's history. `example-shipping-system/` is a fixture outside the reactor and keeps
its own version; leave it alone.

```bash
mvn -B versions:set -DnewVersion=<X.Y.Z> -DgenerateBackupPoms=false
```

Then grep for the version in the docs and fix what has drifted — the jar name appears in
`docs/README.md` and `docs/SKILL.md`, and both have been stale before:

```bash
grep -rn "code-karta-cli-[0-9].*-all.jar\|SNAPSHOT" docs/ README.md
```

The plugin coordinates in `docs/MAVEN-PLUGIN.md` are the one place this is already enforced:
`PluginDescriptorTest.theDocumentedVersionIsTheVersionBeingBuilt` compares them against the
generated descriptor, so `mvn verify` fails rather than shipping a copyable `<plugin>` block
naming the previous version. Nothing enforces the CLI jar name, which is why the grep stays.

### 4. Regenerate the committed diagrams

`docs/diagrams/*.svg` are generated from code-karta's own source, so a renderer or CLI
change moves them. Regenerate and commit them with the release, or the repository ships
diagrams that disagree with the code they describe:

```bash
./gradlew :code-karta-cli:generateDiagrams
git status --short docs/diagrams/
```

Read the diff. Whitespace-only churn across every file usually means a renderer hygiene
change; a structural diff means the architecture moved and is worth a line in the notes.

### 5. Commit and open the PR

```bash
git checkout -b release/v<X.Y.Z>
git commit -am "chore(release): <X.Y.Z>"
gh pr create --fill
```

Wait for CI. Merge only when it is green — CI failing after the tag is pushed is not
something a tag can be taken back from.

### 6. Write the release notes before tagging

Draft them from the commit range, in terms of what a consumer will notice: the flag they
can now pass, the diagram that now renders, the diff that will stop appearing. Not "refactored
KartaCli".

### 7. Stop here and ask

Present to the human:

- the version and what justifies it
- the release notes as drafted
- the exact command that will publish

```bash
git tag -a v<X.Y.Z> -m "<summary>"
git push origin v<X.Y.Z>
```

**Do not run those two commands without explicit approval for this specific version.** The
tag push starts an irreversible publish to Maven Central. Everything up to this point can be
undone with a branch delete.

### 8. After approval

```bash
git push origin v<X.Y.Z>
gh run watch                      # the publish workflow
gh release create v<X.Y.Z> --notes-file <notes>
```

Then confirm the artifact is actually resolvable before telling anyone it shipped — Central
takes a few minutes to index, and a failed publish looks identical to a slow one until you
check:

```bash
mvn -B dependency:get -Dartifact=se.deversity.codekarta:code-karta-cli:<X.Y.Z>:jar:all
mvn -B dependency:get -Dartifact=se.deversity.codekarta:code-karta-maven-plugin:<X.Y.Z>:jar
```

Resolving the plugin is not enough on its own. A plugin has a failure mode the library jars do
not: its descriptor is generated at build time, and one that resolves but carries a missing or
malformed descriptor fails in the user's build rather than in ours. Run a goal to exercise it:

```bash
mvn -B se.deversity.codekarta:code-karta-maven-plugin:<X.Y.Z>:help
```

That has to print the `generate` goal. If it resolves but the help goal does not describe
`generate`, the publish is broken even though every artifact is present, and the fix is a new
version — the published one cannot be replaced.

### 9. Tell the downstream consumers

Repos that pin a code-karta version and regenerate diagrams from it — `async-test-lib`,
`vibetags`, `blindbean` — will not pick up the release on their own. If the release exists
because one of them reported the defect, say so in that repo.
