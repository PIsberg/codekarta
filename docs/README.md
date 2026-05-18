# code-karta

A Java architecture mapping engine that parses source trees and generates SVG diagrams — module dependency maps, class hierarchy diagrams, and method call sequence diagrams.

---

## Prerequisites

| Tool | Minimum version |
|---|---|
| JDK | 21+ |
| Maven | 3.9+ |
| Gradle | use the included `./gradlew` wrapper — no local install needed |

---

## Building

### Maven

```bash
# Compile all modules and run tests
mvn clean test

# Build the CLI fat JAR (includes all dependencies)
mvn clean package
# Output: code-karta-cli/target/code-karta-cli-1.0-SNAPSHOT-all.jar
```

### Gradle

```bash
# Run all tests
./gradlew test

# Build the CLI fat JAR
./gradlew :code-karta-cli:fatJar
# Output: code-karta-cli/build/libs/code-karta-cli-1.0-SNAPSHOT-all.jar
```

---

## Running the CLI

```
java -jar code-karta-cli-1.0-SNAPSHOT-all.jar --input <path> [--output <dir>]
```

| Flag | Required | Default | Description |
|---|---|---|---|
| `--input <path>` | yes | — | Path to parse (see diagram types below) |
| `--output <dir>` | no | `./output` | Directory where SVG files are written (created if absent) |
| `--help` | no | — | Print usage |

### During development (without building the JAR)

```bash
# Maven
mvn -pl code-karta-cli exec:java \
    -Dexec.mainClass=com.karta.cli.KartaCli \
    "-Dexec.args=--input src/domain --output target/diagrams"

# Gradle
./gradlew :code-karta-cli:run --args="--input src/domain --output build/diagrams"
```

---

## Diagram Types

### Module Diagram

**Input:** path to a `module-info.java` file
**Output filename:** `module-diagram.svg`

Shows module boundaries, `requires` dependencies, and `exports` package nodes.

```bash
java -jar karta.jar \
  --input my-project/src/main/java/module-info.java \
  --output diagrams/
```

### Class Diagram

**Input:** path to a directory containing `.java` source files
**Output filename:** `class-diagram.svg`

Shows class and interface nodes with `EXTENDS`, `IMPLEMENTS`, and `HAS` (association) edges.
Standard library types (`String`, `List`, primitives, etc.) are filtered out automatically.

```bash
java -jar karta.jar \
  --input my-project/src/main/java/com/example/domain \
  --output diagrams/
```

### Sequence Diagram

**Input:** path to a single `.java` source file
**Output filename:** `<lowercased-classname>-sequence-diagram.svg`

Traces intra-class method call chains with ordered call edges (label = sequence number).

```bash
java -jar karta.jar \
  --input my-project/src/main/java/com/example/OrderService.java \
  --output diagrams/
```

---

## Where the generated images go

SVG files are written to the `--output` directory (default: `./output/` relative to where the command is run).

```
diagrams/
├── module-diagram.svg
├── class-diagram.svg
└── orderprocessor-sequence-diagram.svg
```

Open any `.svg` file directly in a browser (`File → Open`) or in any IDE that renders SVG.
The files use standard CSS classes (`.node-rect`, `.edge-line`, `.group-rect`, etc.) so they can
be themed by overriding the embedded stylesheet.

### Recommended `.gitignore` entries

```
output/
target/diagrams/
build/diagrams/
```

---

## Example project

A complete fixture project lives in [`example-shipping-system/`](../example-shipping-system/).
Pre-generated diagrams are in [`example-shipping-system/diagrams/`](../example-shipping-system/diagrams/):

| File | What it shows |
|---|---|
| [`module-diagram.svg`](../example-shipping-system/diagrams/module-diagram.svg) | `com.karta.shipping` module → `REQUIRES` java.base / java.logging, `EXPORTS` domain + core packages |
| [`class-diagram.svg`](../example-shipping-system/diagrams/class-diagram.svg) | `ShippingUnit` interface ← `IMPLEMENTS` Cargo ← `EXTENDS` ExpressCargo |
| [`orderprocessor-sequence-diagram.svg`](../example-shipping-system/diagrams/orderprocessor-sequence-diagram.svg) | `OrderProcessor.submit()` → checkStock (1) → reserveStock (2); `cancel()` → releaseStock |

### Regenerate the example diagrams

```bash
JAR=code-karta-cli/target/code-karta-cli-1.0-SNAPSHOT-all.jar

# Build the JAR first if needed
mvn clean package -q

# Module diagram
java -jar $JAR \
  --input example-shipping-system/src/main/java/module-info.java \
  --output example-shipping-system/diagrams/

# Class diagram
java -jar $JAR \
  --input example-shipping-system/src/main/java/com/karta/shipping/domain \
  --output example-shipping-system/diagrams/

# Sequence diagram
java -jar $JAR \
  --input example-shipping-system/src/main/java/com/karta/shipping/core/OrderProcessor.java \
  --output example-shipping-system/diagrams/
```

### Build the example project itself

```bash
cd example-shipping-system

mvn compile          # Maven
./gradlew compileJava  # Gradle
```
