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

Click any thumbnail card to view the full-size vector SVG diagram.

<div align="center">
  <table border="0" style="border-collapse: collapse; border: none; width: 100%; max-width: 900px; margin: auto;">
    <tr style="border: none; background: transparent;">
      <td width="50%" align="center" style="border: none; padding: 12px; vertical-align: top;">
        <div style="border: 1px solid #e5e7eb; border-radius: 12px; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03); overflow: hidden; background: #ffffff; text-align: left;">
          <div style="background: #f9fafb; padding: 10px 16px; border-bottom: 1px solid #f3f4f6; font-weight: bold; color: #1f2937;">
            📦 Module Diagram
          </div>
          <div style="padding: 12px;">
            <a href="docs/diagrams/module-diagram.svg" target="_blank">
              <img src="docs/diagrams/module-diagram.svg" width="100%" style="border-radius: 6px; border: 1px solid #f3f4f6;" alt="Module diagram — JPMS requires/exports"/>
            </a>
            <p style="font-size: 11px; color: #6b7280; margin: 8px 0 0 0; line-height: 1.4;">
              Visualizes Java Platform Module System descriptors (dependencies, required modules, and exported packages).
            </p>
          </div>
        </div>
      </td>
      <td width="50%" align="center" style="border: none; padding: 12px; vertical-align: top;">
        <div style="border: 1px solid #e5e7eb; border-radius: 12px; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03); overflow: hidden; background: #ffffff; text-align: left;">
          <div style="background: #f9fafb; padding: 10px 16px; border-bottom: 1px solid #f3f4f6; font-weight: bold; color: #1f2937;">
            📐 Class Diagram
          </div>
          <div style="padding: 12px;">
            <a href="docs/diagrams/class-diagram.svg" target="_blank">
              <img src="docs/diagrams/class-diagram.svg" width="100%" style="border-radius: 6px; border: 1px solid #f3f4f6;" alt="Class diagram — IR model classes"/>
            </a>
            <p style="font-size: 11px; color: #6b7280; margin: 8px 0 0 0; line-height: 1.4;">
              Maps class relationships, extends/implements hierarchies, and composition associations with filtered standard library types.
            </p>
          </div>
        </div>
      </td>
    </tr>
    <tr style="border: none; background: transparent;">
      <td width="50%" align="center" style="border: none; padding: 12px; vertical-align: top;">
        <div style="border: 1px solid #e5e7eb; border-radius: 12px; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03); overflow: hidden; background: #ffffff; text-align: left;">
          <div style="background: #f9fafb; padding: 10px 16px; border-bottom: 1px solid #f3f4f6; font-weight: bold; color: #1f2937;">
            🔥 Exception-Flow Sequence
          </div>
          <div style="padding: 12px;">
            <a href="docs/diagrams/kartacli-sequence-diagram.svg" target="_blank">
              <img src="docs/diagrams/kartacli-sequence-diagram.svg" width="100%" style="border-radius: 6px; border: 1px solid #f3f4f6;" alt="KartaCli exception-flow sequence diagram"/>
            </a>
            <p style="font-size: 11px; color: #6b7280; margin: 8px 0 0 0; line-height: 1.4;">
              Traces single-file method execution paths paired with robust, color-coded exception propagation routes and try/catch regions.
            </p>
          </div>
        </div>
      </td>
      <td width="50%" align="center" style="border: none; padding: 12px; vertical-align: top;">
        <div style="border: 1px solid #e5e7eb; border-radius: 12px; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03); overflow: hidden; background: #ffffff; text-align: left;">
          <div style="background: #f9fafb; padding: 10px 16px; border-bottom: 1px solid #f3f4f6; font-weight: bold; color: #1f2937;">
            📞 Call-Only Sequence
          </div>
          <div style="padding: 12px;">
            <a href="docs/diagrams/callsequenceparser-sequence-diagram.svg" target="_blank">
              <img src="docs/diagrams/callsequenceparser-sequence-diagram.svg" width="100%" style="border-radius: 6px; border: 1px solid #f3f4f6;" alt="CallSequenceParser call-only sequence diagram"/>
            </a>
            <p style="font-size: 11px; color: #6b7280; margin: 8px 0 0 0; line-height: 1.4;">
              Provides a clean, streamlined method invocation call sequence diagram filtering out catch/throw annotations.
            </p>
          </div>
        </div>
      </td>
    </tr>
    <tr style="border: none; background: transparent;">
      <td width="50%" align="center" style="border: none; padding: 12px; vertical-align: top;">
        <div style="border: 1px solid #e5e7eb; border-radius: 12px; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03); overflow: hidden; background: #ffffff; text-align: left;">
          <div style="background: #f9fafb; padding: 10px 16px; border-bottom: 1px solid #f3f4f6; font-weight: bold; color: #1f2937;">
            🔗 Multi-File Stitched Sequence
          </div>
          <div style="padding: 12px;">
            <a href="docs/diagrams/sequence-diagram.svg" target="_blank">
              <img src="docs/diagrams/sequence-diagram.svg" width="100%" style="border-radius: 6px; border: 1px solid #f3f4f6;" alt="Input layer multi-file stitched sequence diagram"/>
            </a>
            <p style="font-size: 11px; color: #6b7280; margin: 8px 0 0 0; line-height: 1.4;">
              Resolves inter-class and cross-package call flows using JavaParser symbol solving to form a cohesive, unified system view.
            </p>
          </div>
        </div>
      </td>
      <td width="50%" align="center" style="border: none; padding: 12px; vertical-align: top;">
        <div style="border: 1px solid #e5e7eb; border-radius: 12px; box-shadow: 0 4px 6px -1px rgba(0, 0, 0, 0.05), 0 2px 4px -1px rgba(0, 0, 0, 0.03); overflow: hidden; background: #ffffff; text-align: left;">
          <div style="background: #f9fafb; padding: 10px 16px; border-bottom: 1px solid #f3f4f6; font-weight: bold; color: #1f2937;">
            🔄 State Transition Diagram
          </div>
          <div style="padding: 12px;">
            <a href="docs/diagrams/kartacli-state-machine-diagram.svg" target="_blank">
              <img src="docs/diagrams/kartacli-state-machine-diagram.svg" width="100%" style="border-radius: 6px; border: 1px solid #f3f4f6;" alt="KartaCli pipeline state machine — PARSING→LAYOUT→RENDERING→WRITING→DONE"/>
            </a>
            <p style="font-size: 11px; color: #6b7280; margin: 8px 0 0 0; line-height: 1.4;">
              Extracts workflows and states from enums, switch-case state modifications, and fluent builder state DSLs.
            </p>
          </div>
        </div>
      </td>
    </tr>
  </table>
</div>

## Modules

| Module | Purpose |
|---|---|
| `code-karta-core` | Shared graph IR |
| `code-karta-input` | JavaParser-based source analysis |
| `code-karta-layout` | Simple and ELK layout engines |
| `code-karta-render` | SVG rendering |
| `code-karta-cli` | Command-line wrapper |

Agents and contributors can use [`SKILL.md`](SKILL.md) as a compact operating guide for using and extending the library.
