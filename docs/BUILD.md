# Build

code-karta is a dual-build project: Maven is the primary build, Gradle is kept working in
parallel. Both produce the same artifacts from the same sources, and CI enforces that rather than
assuming it: `scripts/check-build-parity.py` fails when the two files name different versions,
and both jobs assert that regeneration leaves the tree clean.

## Requirements

| Tool | Version |
|---|---|
| JDK | 21 (CI runs 21; see the JDK note in [`QUALITY.md`](QUALITY.md)) |
| Maven | 3.9 or newer |
| Gradle | use the included `./gradlew` wrapper |

## Maven (primary)

```bash
mvn clean test                  # compile + run all tests
mvn clean package               # build the fat JAR
mvn clean verify                # the full quality gate — see QUALITY.md
mvn -pl code-karta-input test   # one module
mvn -pl code-karta-input -Dtest=ClassDiagramParserTest test   # one test class
```

The Maven fat JAR is written to:

```text
code-karta-cli/target/code-karta-cli-0.3.0-all.jar
```

## Gradle

```bash
./gradlew test                      # run all tests
./gradlew :code-karta-input:test    # one module
./gradlew :code-karta-cli:fatJar    # build the fat JAR
```

The Gradle fat JAR is written to:

```text
code-karta-cli/build/libs/code-karta-cli-0.3.0-all.jar
```

## Running the CLI without building a JAR

```bash
# Maven
mvn -pl code-karta-cli exec:java \
  -Dexec.mainClass=se.deversity.codekarta.cli.KartaCli \
  "-Dexec.args=--input example-shipping-system/src/main/java/com/karta/shipping/domain --output target/diagrams"

# Gradle
./gradlew :code-karta-cli:run \
  --args="--input example-shipping-system/src/main/java/com/karta/shipping/domain --output build/diagrams"
```

## Dependency versions

Shared versions live in `pom.xml` and are mirrored in `build.gradle.kts`. Dependabot only edits
`pom.xml`, so the mirror has to be updated by hand:

```bash
python3 scripts/check-build-parity.py   # fails when the two disagree
```

CI runs this as its own job. It went in after the Gradle build was found running jspecify 1.0.0
and junit 6.1.2 against a pom that said 1.0.1 and 6.1.3, which meant the two CI jobs were testing
different dependency sets without saying so.

The Gradle build resolves from Maven Central only. `mavenLocal()` is opt-in:

```bash
./gradlew test -PuseMavenLocal   # allow locally installed artifacts to satisfy the build
```

Leaving it off by default is deliberate. With `mavenLocal()` always first, a dependency that
exists only because it was built on this machine satisfies the build, so an unpublished version
looks green locally and fails in CI.

## Diagram regeneration

`mvn verify` and `./gradlew :code-karta-cli:generateDiagrams` run the built JAR against
code-karta's own source to refresh `docs/diagrams/`. The run is idempotent — identical input
produces byte-identical SVG — so a clean tree stays clean.

Both CI jobs check this with `git diff --exit-code` after regenerating. Downstream repositories
commit the diagrams this produces, so byte drift arrives in someone else's repository as a diff
nobody made; that is what the 0.2.0 SVG hygiene work was about. If the step fails, either the
invariant broke or a regeneration was not committed.

```bash
mvn verify -DskipTests -DskipDiagrams=true              # skip regeneration
./gradlew :code-karta-cli:generateDiagrams -PskipDiagrams
```

## Annotation processing

Both builds run the [VibeTags](https://github.com/PIsberg/vibetags) annotation processor over
**main sources only**. Test sources compile with `-proc:none` on purpose — see
[`VIBETAGS.md`](VIBETAGS.md) for why, and for the rule that follows from it.

Maven resolves processors through `annotationProcessorPaths`, which replaces classpath
discovery: a processor missing from that list is silently skipped rather than failing the build.

## Version constant

`0.3.0` appears in `pom.xml`, `build.gradle.kts`, and the JAR paths named throughout these docs.
`scripts/check-build-parity.py` checks the first two against each other and fails the build when
they drift. The JAR paths written in these docs are not checked by anything, so a version bump
still means changing them by hand.
