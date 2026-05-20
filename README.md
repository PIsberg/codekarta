# code-karta

code-karta is a Java architecture mapping tool. It parses Java source and emits SVG diagrams for modules, class relationships, method call sequences, exception flow, and stitched multi-file call graphs.

Start with the full guide in [`docs/README.md`](docs/README.md). The architecture notes are in [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md).

## Quick Start

```bash
mvn clean package

java -jar code-karta-cli/target/code-karta-cli-1.0-SNAPSHOT-all.jar \
  --input example-shipping-system/src/main/java/com/karta/shipping/domain \
  --output output
```

Common modes:

```bash
# JPMS module diagram
java -jar code-karta-cli/target/code-karta-cli-1.0-SNAPSHOT-all.jar \
  --input example-shipping-system/src/main/java/module-info.java \
  --output output

# Single-file sequence and exception-flow diagram
java -jar code-karta-cli/target/code-karta-cli-1.0-SNAPSHOT-all.jar \
  --input example-shipping-system/src/main/java/com/karta/shipping/core/OrderProcessor.java \
  --output output

# Multi-file stitched call graph, better layout for larger diagrams
java -jar code-karta-cli/target/code-karta-cli-1.0-SNAPSHOT-all.jar \
  --input code-karta-input/src/main/java \
  --output output \
  --sequence-only \
  --layout elk
```

## Modules

| Module | Purpose |
|---|---|
| `code-karta-core` | Shared graph IR |
| `code-karta-input` | JavaParser-based source analysis |
| `code-karta-layout` | Simple and ELK layout engines |
| `code-karta-render` | SVG rendering |
| `code-karta-cli` | Command-line wrapper |

Agents and contributors can use [`SKILL.md`](SKILL.md) as a compact operating guide for using and extending the library.
