# code-karta

code-karta is a Java architecture mapping tool. It parses Java source and emits SVG diagrams for modules, class relationships, method call sequences, exception flow, stitched multi-file call graphs, and enum-backed state machines.

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

# Multi-file stitched call graph — OrderProcessor→InventoryService across files
java -jar code-karta-cli/target/code-karta-cli-1.0-SNAPSHOT-all.jar \
  --input example-shipping-system/src/main/java/com/karta/shipping/core \
  --output output \
  --sequence-only

# State transition diagram — KartaCli's own pipeline (PARSING→LAYOUT→RENDERING→WRITING→DONE)
java -jar code-karta-cli/target/code-karta-cli-1.0-SNAPSHOT-all.jar \
  --input code-karta-cli/src/main/java/com/karta/cli/KartaCli.java \
  --output output \
  --state-machine
```

## Examples

Click any thumbnail to open the full-size SVG.

<table>
  <tr>
    <th>Module diagram</th>
    <th>Class diagram</th>
  </tr>
  <tr>
    <td><a href="docs/diagrams/module-diagram.svg"><img src="docs/diagrams/module-diagram.svg" width="360" alt="Module diagram — JPMS requires/exports"/></a></td>
    <td><a href="docs/diagrams/class-diagram.svg"><img src="docs/diagrams/class-diagram.svg" width="360" alt="Class diagram — IR model classes"/></a></td>
  </tr>
  <tr>
    <th>Exception-flow sequence</th>
    <th>Call-only sequence</th>
  </tr>
  <tr>
    <td><a href="docs/diagrams/kartacli-sequence-diagram.svg"><img src="docs/diagrams/kartacli-sequence-diagram.svg" width="360" alt="KartaCli exception-flow sequence diagram"/></a></td>
    <td><a href="docs/diagrams/callsequenceparser-sequence-diagram.svg"><img src="docs/diagrams/callsequenceparser-sequence-diagram.svg" width="360" alt="CallSequenceParser call-only sequence diagram"/></a></td>
  </tr>
  <tr>
    <th>Multi-file stitched sequence</th>
    <th>State transition</th>
  </tr>
  <tr>
    <td><a href="docs/diagrams/sequence-diagram.svg"><img src="docs/diagrams/sequence-diagram.svg" width="360" alt="Input layer multi-file stitched sequence diagram"/></a></td>
    <td><a href="docs/diagrams/kartacli-state-machine-diagram.svg"><img src="docs/diagrams/kartacli-state-machine-diagram.svg" width="360" alt="KartaCli pipeline state machine — PARSING→LAYOUT→RENDERING→WRITING→DONE"/></a></td>
  </tr>
</table>

## Modules

| Module | Purpose |
|---|---|
| `code-karta-core` | Shared graph IR |
| `code-karta-input` | JavaParser-based source analysis |
| `code-karta-layout` | Simple and ELK layout engines |
| `code-karta-render` | SVG rendering |
| `code-karta-cli` | Command-line wrapper |

Agents and contributors can use [`SKILL.md`](SKILL.md) as a compact operating guide for using and extending the library.
