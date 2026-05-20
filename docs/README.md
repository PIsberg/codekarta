# code-karta

code-karta turns Java source into SVG architecture diagrams. It can map JPMS modules, class relationships, single-file call flows, exception propagation, and multi-file call sequences.

The project is intentionally small and compiler-like:

1. Parse Java source into a shared graph IR.
2. Assign coordinates with a layout engine.
3. Render the graph as SVG.

## Requirements

| Tool | Version |
|---|---|
| JDK | 21 or newer |
| Maven | 3.9 or newer |
| Gradle | use the included `./gradlew` wrapper |

## Build

Maven:

```bash
mvn clean test
mvn clean package
```

The Maven fat JAR is written to:

```text
code-karta-cli/target/code-karta-cli-1.0-SNAPSHOT-all.jar
```

Gradle:

```bash
./gradlew test
./gradlew :code-karta-cli:fatJar
```

The Gradle fat JAR is written to:

```text
code-karta-cli/build/libs/code-karta-cli-1.0-SNAPSHOT-all.jar
```

## CLI

```bash
java -jar code-karta-cli/target/code-karta-cli-1.0-SNAPSHOT-all.jar \
  --input <path> \
  --output <dir> \
  [--sequence-only] \
  [--layout simple|elk]
```

| Flag | Default | Description |
|---|---|---|
| `--input <path>` | required | Java source path to parse. The path determines the diagram type. |
| `--output <dir>` | `./output` | Directory where the SVG file is written. Created if missing. |
| `--sequence-only` | off | Emits only `CALLS` edges. For directory input, parses all Java files together into one stitched sequence graph. |
| `--layout simple|elk` | `simple` | `simple` is the pure-Java BFS grid. `elk` uses Eclipse Layout Kernel layered layout and is better for larger graphs. |
| `--help` | off | Prints CLI usage. |

During development:

```bash
mvn -pl code-karta-cli exec:java \
  -Dexec.mainClass=com.karta.cli.KartaCli \
  "-Dexec.args=--input example-shipping-system/src/main/java/com/karta/shipping/domain --output target/diagrams"

./gradlew :code-karta-cli:run --args="--input example-shipping-system/src/main/java/com/karta/shipping/domain --output build/diagrams"
```

## Diagram Modes

| Input | Mode | Output file |
|---|---|---|
| `module-info.java` | JPMS module diagram | `module-diagram.svg` |
| Directory | Class diagram | `class-diagram.svg` |
| Single `.java` file | Exception-flow sequence diagram | `<lowercase-classname>-sequence-diagram.svg` |
| Single `.java` file plus `--sequence-only` | Call-only sequence diagram | `<lowercase-classname>-sequence-diagram.svg` |
| Directory plus `--sequence-only` | Multi-file stitched sequence diagram | `sequence-diagram.svg` |

### Module Diagram

Parses a JPMS module descriptor into module and package nodes with `REQUIRES` and `EXPORTS` edges.

```bash
java -jar code-karta-cli/target/code-karta-cli-1.0-SNAPSHOT-all.jar \
  --input example-shipping-system/src/main/java/module-info.java \
  --output example-shipping-system/diagrams
```

### Class Diagram

Parses a directory of Java files into class and interface nodes with `EXTENDS`, `IMPLEMENTS`, and `HAS` edges. Standard library types such as `String`, primitives, and common collections are filtered out so diagrams focus on domain types.

```bash
java -jar code-karta-cli/target/code-karta-cli-1.0-SNAPSHOT-all.jar \
  --input example-shipping-system/src/main/java/com/karta/shipping/domain \
  --output example-shipping-system/diagrams
```

### Exception-Flow Sequence Diagram

Parses one Java file into method nodes and ordered call edges. The default file mode also includes exception propagation and try/catch regions when the parser can identify them.

```bash
java -jar code-karta-cli/target/code-karta-cli-1.0-SNAPSHOT-all.jar \
  --input code-karta-cli/src/main/java/com/karta/cli/KartaCli.java \
  --output docs/diagrams
```

### Call-Only Sequence Diagram

Use `--sequence-only` when you want a cleaner call graph without exception-flow annotations.

```bash
java -jar code-karta-cli/target/code-karta-cli-1.0-SNAPSHOT-all.jar \
  --input code-karta-input/src/main/java/com/karta/input/parser/CallSequenceParser.java \
  --output docs/diagrams \
  --sequence-only
```

### Multi-File Stitched Sequence Diagram

Directory input plus `--sequence-only` parses all `.java` files below the directory and uses JavaParser symbol solving to stitch calls across files. Point `--input` at the source root, such as `src/main/java`, when you need cross-package resolution.

```bash
java -jar code-karta-cli/target/code-karta-cli-1.0-SNAPSHOT-all.jar \
  --input code-karta-input/src/main/java \
  --output docs/diagrams \
  --sequence-only \
  --layout elk
```

## Generated Files

SVG files are written to `--output`, or to `./output` when `--output` is omitted.

```text
output/
├── module-diagram.svg
├── class-diagram.svg
├── kartacli-sequence-diagram.svg
└── sequence-diagram.svg
```

The SVGs are self-contained and open directly in a browser or IDE. Rendered elements use stable CSS classes such as `.node-rect`, `.edge-line`, and `.group-rect`; library users can pass a custom stylesheet to `SvgRenderer.render(graph, css)`.

Recommended local ignore rules:

```gitignore
output/
target/diagrams/
build/diagrams/
```

## Library Use

The CLI is only a thin wrapper. Java code can use the modules directly:

```java
Path input = Path.of("src/main/java");
Graph graph = new JavaSourceInputParser().parse(input);
new ElkLayoutEngine().layout(graph);
String svg = new SvgRenderer().render(graph);
```

Use `new JavaSourceInputParser(true)` for call-only single-file parsing. Use `new MultiFileSequenceParser().parse(sourceRoot)` for stitched sequence graphs.

## Example Project

A complete fixture lives in [`example-shipping-system/`](../example-shipping-system/). Pre-generated diagrams are in [`example-shipping-system/diagrams/`](../example-shipping-system/diagrams/).

| File | Shows |
|---|---|
| [`module-diagram.svg`](../example-shipping-system/diagrams/module-diagram.svg) | JPMS `requires` and `exports` from the shipping module descriptor |
| [`class-diagram.svg`](../example-shipping-system/diagrams/class-diagram.svg) | `ShippingUnit`, `Cargo`, and `ExpressCargo` relationships |
| [`orderprocessor-sequence-diagram.svg`](../example-shipping-system/diagrams/orderprocessor-sequence-diagram.svg) | `OrderProcessor` method calls |

Regenerate those diagrams:

```bash
mvn clean package -q

JAR=code-karta-cli/target/code-karta-cli-1.0-SNAPSHOT-all.jar

java -jar $JAR --input example-shipping-system/src/main/java/module-info.java --output example-shipping-system/diagrams
java -jar $JAR --input example-shipping-system/src/main/java/com/karta/shipping/domain --output example-shipping-system/diagrams
java -jar $JAR --input example-shipping-system/src/main/java/com/karta/shipping/core/OrderProcessor.java --output example-shipping-system/diagrams
```

Build the example itself:

```bash
cd example-shipping-system
mvn compile
./gradlew compileJava
```

## Architecture

For module responsibilities, graph schema, rendering rules, and extension points, read [`ARCHITECTURE.md`](ARCHITECTURE.md).
