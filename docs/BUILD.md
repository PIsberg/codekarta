# Build

code-karta is a dual-build project: Maven is the primary build, Gradle is kept working in
parallel. Both produce the same artifacts from the same sources.

## Requirements

| Tool | Version |
|---|---|
| JDK | 21 or newer |
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
code-karta-cli/target/code-karta-cli-0.2.0-all.jar
```

## Gradle

```bash
./gradlew test                      # run all tests
./gradlew :code-karta-input:test    # one module
./gradlew :code-karta-cli:fatJar    # build the fat JAR
```

The Gradle fat JAR is written to:

```text
code-karta-cli/build/libs/code-karta-cli-0.2.0-all.jar
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

## Diagram regeneration

`mvn verify` and `./gradlew :code-karta-cli:generateDiagrams` run the built JAR against
code-karta's own source to refresh `docs/diagrams/`. The run is idempotent — identical input
produces byte-identical SVG — so a clean tree stays clean.

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

`0.2.0` appears in `pom.xml`, `build.gradle.kts`, and the JAR paths named throughout these docs.
They are not checked against each other by any test — change one, change all.
