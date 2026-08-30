# Changelog

All notable changes to code-karta are documented here.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and this project
adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

Entries before 0.3.0 were backfilled from git history and the GitHub release notes, so they
summarise each release rather than reproducing every commit. The `Full changelog` compare link on
each version heading is the complete record.

## [Unreleased]

## [0.4.0] - 2026-08-30

### Changed

- Dependency updates: PMD 7.26.0 to 7.27.0, maven-enforcer-plugin 3.6.2 to 3.6.3, Maven plugin
  tools 3.15.1 to 3.15.2, maven-invoker-plugin 3.9.0 to 3.10.1, Groovy 4.0.28 to 5.0.8 (a major
  bump; it only runs the invoker integration-test scripts, which pass unchanged), and PIT 1.25.9
  to 1.30.0. Jackson, JavaParser, JUnit, ELK, Xtext, Checkstyle, Error Prone, JaCoCo, SpotBugs,
  ArchUnit, JSpecify and VibeTags were already at their latest stable releases; the pre-release
  lines offered for the compiler, source, surefire and plugin plugins (4.0.0-beta/3.6.0-M1) and
  Maven 4.0.0-rc are not taken, and the Maven 3.9.x API pin is deliberate and documented in the
  parent pom.
- `COMPATIBILITY.md` moved to `docs/COMPATIBILITY.md`, joining the rest of the documentation;
  only the GitHub community-health files, the agent briefings and `llms.txt` stay in the root.
  All links and mentions were updated, and `docs/README.md` now indexes it.

### Added

- **`code-karta-maven-plugin`, with a `karta:generate` goal.** Diagram generation now binds into
  a consumer's Maven build instead of requiring a hand-run jar. The parameters and a copyable
  `<plugin>` block are in `docs/MAVEN-PLUGIN.md`, and `PluginDescriptorTest` compares the
  version documented there against the generated plugin descriptor, so the docs cannot name a
  stale version without failing `mvn verify`. The goal is exercised by `maven-invoker-plugin`
  integration tests that run it from real Maven builds, including an aggregator-skip and a
  fail-on-empty case.

- A parser evaluation suite that runs against Java nobody here wrote. Parsers never crash by
  contract, which makes a parsing regression silent: the graph gets smaller and every test still
  passes. `JdkCorpusTest` now parses the building JDK's own `src.zip` on every build (135 files
  of `java.util`, measured 138 nodes on JDK 21, floor at 110), and a weekly workflow runs
  `scripts/run-corpus.py` over four projects pinned by commit (guava, picocli, junit5,
  jackson-databind) with per-project node floors.

- **`--format json`, and `JsonRenderer` for library callers.** code-karta could only produce SVG,
  which is a dead end for tooling: an architecture rule that should fail a build, a diff between
  two revisions, a report, or a consumer's own renderer all need the graph, not a drawing of it.
  `JsonRenderer` writes the IR verbatim, including layout coordinates when a layout engine has
  run, and it round-trips back into a `Graph`. Output is byte-identical across runs, which two
  tests pin: `HashMap` iteration order and `DefaultPrettyPrinter`'s platform line separator would
  each have broken it silently, and `KartaCli.run`'s idempotence rests on it.

  This also puts the Jackson dependency to work. All four model classes carried Jackson
  annotations and no `ObjectMapper` existed anywhere in `src/main`, so until now the dependency
  was pure supply-chain surface with nothing behind it.

- `RunOptions` gained a `format` component and `withFormat`. The previous eight-argument
  constructor is kept and delegates with the format defaulted to `svg`, so existing callers
  compile and link unchanged.

- Javadoc on the public API of `code-karta-core`. It had none: zero `/**` blocks across all seven
  model classes, in the module every library consumer imports, while the release profile published
  a javadoc jar built with `doclint=none`. `Graph`, `Node`, `Edge`, `Group` and the package
  documentation now state the things the signatures do not, including the null-coordinate contract
  between layout and render, that duplicate node ids are permitted and the first wins, that edge
  endpoints need not resolve, and what `Edge.label` means per edge type. `InputParser` and
  `JavaSourceInputParser` document the fault-tolerance contract and its consequence: an empty
  graph does not distinguish "nothing to draw" from "nothing parsed".
- `doclint` is now `all,-missing` instead of `none`, so a broken `@link`, a wrong `@param` name or
  malformed HTML fails the release build. Confirmed by breaking a reference deliberately: the
  build fails with "reference not found". `-missing` is deliberate, so the gate does not demand a
  comment on every accessor and get switched off again.

- Both build wrappers now pin the distribution they will run by SHA-256
  (`distributionSha256Sum`). Without it a wrapper executes whatever bytes come back from its
  distribution URL, on every developer machine and every CI runner. The Maven value was derived
  by fetching `apache-maven-3.9.11-bin.zip`, checking it against the SHA-512 that
  `downloads.apache.org` publishes over TLS, and hashing the file that matched; the Gradle value
  is the one Gradle publishes at `<distributionUrl>.sha256`. Verified in both directions: a wrong
  sum fails with "Failed to validate Maven distribution SHA-256", and a fresh download with the
  correct sum succeeds. CI also runs `gradle/actions/wrapper-validation` against the committed
  `gradle-wrapper.jar`, which is a binary every build executes before any other check.

- **All five modules now compile for Java 17** instead of 21, so an application still on 17 can
  both depend on the library and run the CLI jar. Nothing in the codebase used a language or API
  feature past 17; the 21 floor was inherited rather than required. Verified end to end rather
  than by the compiler flag alone: a new `java17` CI job compiles a consumer with JDK 17 and runs
  it on JDK 17, runs the shipped fat jar on JDK 17, and asserts every jar carries class file major
  version 61. Locally the same consumer ran on JDK 17.0.5 and produced the same 11170-byte SVG as
  on 21.

  One runtime caveat, documented in `docs/COMPATIBILITY.md` and `docs/CLI.md`: ELK's transitive
  `org.eclipse.xtext.xbase.lib` is compiled for Java 21 in every published version, so on a Java
  17 runtime `--layout elk` falls back to the simple layout and logs a warning.

- A Maven wrapper (`./mvnw`, pinned to 3.9.11). `mvn clean verify` on Maven 3.8.6 fails with a
  bare `PluginIncompatibleException`, because the SpotBugs plugin declares a 3.8.9 prerequisite
  and nothing in this repository declared a minimum. CI and the docs now use the wrapper, so
  contributors and CI run the same Maven.
- A `maven-enforcer-plugin` toolchain gate. `requireMavenVersion [3.8.9,)` and
  `requireJavaVersion [21,26)` fail in `validate` with a message naming the toolchain, instead
  of surfacing later as a SpotBugs plugin error or a JaCoCo instrumentation error that both read
  like defects in this repository. Verified in both directions: green on JDK 21, and red on
  JDK 26 with the intended message. This closes the follow-up noted in the 0.3.0 entry below.

- `LICENSE` (MIT). The repository had no license file at all. GitHub reported the license as
  `null` and an automated OSS review had nothing to read, which is a hard stop for adoption in
  most companies regardless of what the README badge claimed.
- `SECURITY.md`, with a threat model, supported versions and private vulnerability reporting.
- `CONTRIBUTING.md`, `CODE_OF_CONDUCT.md` (Contributor Covenant 2.1), issue forms and a pull
  request template.
- `COMPATIBILITY.md` (now `docs/COMPATIBILITY.md`), stating what counts as public API, what does not, and what a version bump
  is allowed to break. Previously a consumer had to infer the boundary from the source.

### Fixed

- **Graph construction was O(n²).** `Graph.findNode` and `Graph.addNodeIfAbsent` were
  `nodes.stream()` linear scans, and they are called once per class, method and call site by every
  parser and once per node by both layout engines, at 24 sites. Both are now backed by an id
  index. Measured on the same machine, JDK 21, 20,000 nodes:

  | | scan | indexed |
  |---|---|---|
  | 20,000 `addNodeIfAbsent` | 1417 ms | 9 ms |
  | 20,000 `findNode` | 7492 ms | 1 ms |

  Scaling matters more than the absolute numbers: doubling 10k to 20k took `findNode` from 407 ms
  to 7492 ms, an 18x rise for 2x the input. The index is derived state, rebuilt whenever the node
  list length changes, so nodes added or removed through `getNodes()` are still found. Replacing
  an element in place, or removing one and adding another between lookups, is not covered and is
  documented as unsupported.

- **`ElkLayoutEngine` did not fall back on the failures ELK actually produces.** Its javadoc
  promises a transparent fallback to `SimpleLayoutEngine` "for any reason", but it caught only
  `Exception`. ELK resolves its algorithms through `ServiceLoader`, so its two realistic failures
  are `ServiceConfigurationError` (a shaded jar that did not merge `META-INF/services`) and
  `LinkageError` (a dependency built for a newer JDK than the runtime) and both are `Error`s that
  went straight through. On a Java 17 runtime this is the normal case, not an edge case, because
  `org.eclipse.xtext.xbase.lib` is compiled for 21. The catch is now
  `Exception | LinkageError | ServiceConfigurationError`; `OutOfMemoryError` and other Errors
  still propagate, which a test pins. Three regression tests were written first and confirmed red
  before the fix.

- **The published `code-karta-cli` artifact could not be used as a dependency.** The shade
  plugin was generating a dependency-reduced pom that replaced the real one, and because the
  shaded jar is an attached `all` classifier rather than the main artifact, the reduced pom
  stripped the four compile dependencies the unshaded main jar actually needs. Verified against
  Maven Central: `code-karta-cli-0.3.0.pom` declares three dependencies, all of them `test` or
  `provided` scope, and none of `code-karta-core`, `-input`, `-layout` or `-render`. Declaring
  `code-karta-cli` as a Maven dependency therefore resolved a jar of three classes with nothing
  to run them against. `createDependencyReducedPom` is now `false`, the generated file is
  untracked and ignored, and a CI step fails if it returns. Affects 0.1.0, 0.2.0 and 0.3.0; the
  fat jar (`-all` classifier) was never affected.

- The declared license disagreed with itself. `pom.xml` declared Apache-2.0 while the README
  badge showed MIT, so the three artifacts published to Maven Central (0.1.0, 0.2.0, 0.3.0)
  carry an Apache-2.0 declaration that was never the intent. The project is MIT; `pom.xml` now
  says so, and the next release corrects the published metadata.
- The Maven Central badge linked to the 0.1.0 artifact page rather than the artifact.

## [0.3.0] - 2026-08-23

### Changed

- VibeTags annotation processor 1.0.3 to 1.1.1. 1.0.4 was prepared upstream but never tagged or
  published, so this repository moves from 1.0.3 straight to the 1.1.x line and picks up both sets
  of changes. 1.1.1 fixes a missing `Platform.GEMINI_GRANULAR` arm in the upstream renderer
  registry, which matters here because this repository opted into Gemini granular rules in #75.
  1.1.1 changes generated output, so the committed guardrail files under `.claude/rules/`,
  `CLAUDE.md` and `GEMINI.md` are regenerated in the same change.
- VibeTags 1.1.1 to 1.2.5 (#97 and this release). Generated output is byte-identical to 1.2.0:
  `mvn clean verify` regenerated `CLAUDE.md`, `GEMINI.md`, `llms.txt`, `.claude/rules/` and
  `docs/diagrams/` with no diff, and the Gradle regeneration matched.
- Dependency updates over the whole 0.2.0 to 0.3.0 range: Checkstyle 13.9.0 to 14.0.0 (#105,
  #110), Jackson 2.22.1 to 2.22.2 (#106, #109), JUnit 6.1.2 to 6.1.3 (#82), JaCoCo 0.8.13 to
  0.8.15 (#88), SpotBugs Maven plugin 4.10.3.0 to 4.10.4.0, JSpecify 1.0.0 to 1.0.1 (#76),
  PIT 1.25.8 to 1.25.9 (#80), ArchUnit (#78, #90), CycloneDX Maven plugin (#77), the Shadow
  Gradle plugin 9.6.0 to 9.6.1 (#91), and the GitHub Actions: cache (#93), setup-java (#94),
  CodeQL to v4.37.8 (#70, #104), Scorecard (#69), harden-runner (#102).
- The Gradle wrapper moves from 9.5.1 to 9.7.1. `./gradlew wrapper` rewrites
  `gradle-wrapper.properties` from its own defaults, which resets `retries` to 0. That is the
  single-CDN-blip failure this file's own Fixed entry below describes, so the bump restores
  `retries=3` and `retryBackOffMs=2000` explicitly.
- SpotBugs Maven plugin 4.10.4.0 requires Maven 3.8.9 or newer. Nothing in this repository
  declares a minimum Maven version, so on an older Maven the build now fails with the plugin's
  own message rather than a version check. See the follow-up issue linked from the release PR.
- PMD 7.26.0, Error Prone 2.50.0, JavaParser 3.28.2 and ELK 0.12.0 were already at their latest
  releases. Xtext stays at 2.43.0: the only newer version published is the 2.44.0.M3 milestone.

### Added

- An indexed VibeTags root (#74), so an agent session no longer loads every module's guardrails
  up front. A `.vibetags-root-index` marker keeps the always-on safety tier (`@AILocked`,
  `@AICore`, `@AIAudit`) inline in `CLAUDE.md` and indexes the rest into 26 per-module files
  under `<module>/.claude/rules/`, which load when a matching source file is opened. Root
  `CLAUDE.md`: 537 lines to 137. Verified against the tree: 137 lines, marker present, 26 rule
  files.
- This changelog. Release history previously lived only in GitHub Releases.
- `scripts/check-build-parity.py`, run as its own CI job, fails the build when `pom.xml` and
  `build.gradle.kts` name different versions. They had already drifted on `main`: the Gradle
  build resolved jspecify 1.0.0 and junit 6.1.2 against a pom that said 1.0.1 and 6.1.3, so the
  two CI jobs were testing different dependency sets. A version that cannot be read from either
  file fails too, so a rename cannot pass as agreement.
- A `gradle` ecosystem entry in `dependabot.yml`. Only `maven` and `github-actions` were declared,
  which is why the Gradle build had no way to keep up.
- JaCoCo line-coverage reporting and a per-module floor. Measured: core 100%, layout 96.1%,
  render 95.8%, input 82.7%, cli 45.0%, overall 83.5%.
- A mutation-score floor per module, and a CI job that runs PIT. The `mutation` profile had
  existed for months with no workflow referencing it. Measured: core 100%, layout 76.4%,
  input 68.1%, cli 34.5%, render 24.1%, overall 49.7% of 1203 mutants.
- A CI step asserting that regeneration is idempotent and committed. `KartaCli.run` is documented
  as producing byte-identical SVG, and downstream repositories commit its output, but nothing
  checked it.
- `.gitattributes`, so generated files have the same bytes regardless of which OS built them.

### Fixed

- `build.gradle.kts` no longer puts `mavenLocal()` ahead of Maven Central unconditionally; it is
  opt-in via `-PuseMavenLocal`. An artifact present only because it was built on the local
  machine could satisfy the build, which is how a bump to an unpublished VibeTags 1.0.4 passed
  locally while being unresolvable from Central.
- CI ran `mvn clean test` and then `mvn clean verify -DskipTests`, so the second clean discarded
  the first run's output and no run ever executed the tests and the static-analysis gate
  together. It is now one `mvn -B clean verify`.
- The push trigger now includes `chore/**` and `docs/**`; branches with those prefixes got no CI
  until a pull request opened.
- Source files walked from disk are sorted. `Files.walk` returns entries in a filesystem-defined
  order — NTFS by name, ext4 with `dir_index` by hash — and no walk site sorted, so the same
  source tree produced one node order on Windows and another on Linux. `KartaCli.run` was
  idempotent per machine, not across machines, which is the case that matters when downstream
  repositories commit the generated SVGs. Found by the new idempotency step on its first run.
- `gradlew` and `example-shipping-system/gradlew` are committed executable. CI was running
  `chmod +x` to compensate, which is also a papercut when cloning on Linux or macOS.
- The Gradle wrapper retries its distribution download (`retries=0` meant a single CDN blip
  failed the job) and the example job caches the distribution instead of fetching it every run.

## [0.2.0] - 2026-07-31

Five defects reported by a downstream repository that generates and commits code-karta diagrams,
plus one found while verifying the fix against it.

No published signature changed. `KartaCli.run(...)` gained a `RunOptions` overload; the existing
overloads remain and delegate to it.

### Added

- `--modules-only` reads Maven `<modules>` and Gradle `include(...)`, not just JPMS. A reactor
  without a `module-info.java` previously produced `Graph is empty`. `HAS` edges carry
  aggregation, `REQUIRES` carries dependencies on siblings inside the same reactor;
  dependencies outside it are dropped. `module-info.java` still wins when both are declared.
  Poms are parsed with entity resolution off, since the input is somebody else's build file.
- `--output-name <file>` names the output file. Every class diagram was previously called
  `class-diagram.svg`, so N diagrams needed N output directories. The name is treated as data
  rather than as a path: anything that would land outside `--output` is refused and the derived
  name is used instead.
- `--max-members <n>|all` exposes the `...(+N more)` compartment cap, previously a private
  constant fixed at 6.
- `--split-packages` emits one diagram per package.
- A warning when the rendered diagram is too large to read, measured against the rendered canvas.

### Fixed

- Generated SVGs end with a newline and carry no trailing whitespace, so `end-of-file-fixer` and
  `trailing-whitespace` hooks no longer rewrite every regenerated file. Both renderers assert this
  line by line. **This changes the bytes of every generated diagram**: regenerating after
  upgrading shows one diff per file, and is stable after that.
- A relative `--input` normalised the root pom to the bare path `pom.xml`, whose parent is `null`,
  so every `<module>` looked unreadable and the Gradle fallback quietly produced a
  plausible-looking diagram instead.
- The layout wraps a depth level instead of emitting one unbounded row.
- A state-machine diagram with no transitions is declined rather than rendered empty.
- Labels fit inside diagram boxes, and diagrams stay project-only.

## [0.1.0] - 2026-07-23

First published release, under the `se.deversity.codekarta` group on Maven Central.

### Added

- The three-tier pipeline: `code-karta-core` (IR), `code-karta-input`, `code-karta-layout`,
  `code-karta-render`, wired by `code-karta-cli`. `ArchitectureRulesTest` enforces the tier
  boundaries with ArchUnit.
- Java source parsers for class, sequence, and multi-file stitched sequence graphs, with symbol
  resolution and a stdlib and logging noise filter.
- Two layout engines: a simple built-in engine and an ELK layered engine behind `--layout`.
- SVG rendering with UML compartments, sequence lifelines, and consumer-themeable CSS classes.
- The `mvn verify` quality gate: Checkstyle, PMD with CPD, SpotBugs, Error Prone, PIT mutation
  testing behind a profile, JSpecify nullness annotations, and CycloneDX SBOM generation.
- VibeTags annotations across all modules, generating the guardrail regions in `CLAUDE.md` and
  the per-module rules.
- GitHub Actions for build, test, and Maven Central publishing.

[Unreleased]: https://github.com/PIsberg/codekarta/compare/v0.4.0...HEAD
[0.4.0]: https://github.com/PIsberg/codekarta/compare/v0.3.0...v0.4.0
[0.3.0]: https://github.com/PIsberg/codekarta/compare/v0.2.0...v0.3.0
[0.2.0]: https://github.com/PIsberg/codekarta/compare/v0.1.0...v0.2.0
[0.1.0]: https://github.com/PIsberg/codekarta/releases/tag/v0.1.0
