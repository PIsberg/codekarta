# code-karta

[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Build and Test](https://github.com/PIsberg/codekarta/actions/workflows/build.yml/badge.svg)](https://github.com/PIsberg/codekarta/actions/workflows/build.yml)
[![Java 21+](https://img.shields.io/badge/Java-21+-orange?logo=openjdk)](https://github.com/PIsberg/codekarta)
[![Maven](https://img.shields.io/badge/build-Maven-blue?logo=apachemaven)](https://github.com/PIsberg/codekarta)
[![Gradle](https://img.shields.io/badge/build-Gradle-blue?logo=gradle)](https://github.com/PIsberg/codekarta)
[![ArchUnit](https://img.shields.io/badge/ArchUnit-passing-brightgreen?logo=apachemaven&logoColor=white)](https://github.com/PIsberg/codekarta/blob/main/code-karta-cli/src/test/java/com/karta/cli/ArchitectureRulesTest.java)
[![PMD](https://img.shields.io/badge/PMD-passing-brightgreen)](https://pmd.github.io/)
[![SpotBugs](https://img.shields.io/badge/SpotBugs-passing-brightgreen)](https://spotbugs.github.io/)
[![JSpecify](https://img.shields.io/badge/JSpecify-null--marked-blue)](https://jspecify.dev/)
[![PIT Mutation Testing](https://img.shields.io/badge/PIT%20Mutation-enabled-brightgreen?logo=apachemaven&logoColor=white)](https://github.com/PIsberg/codekarta/actions/workflows/build.yml)
[![Lines of Code](https://www.aschey.tech/tokei/github/PIsberg/codekarta?languages=Java&category=code)](https://github.com/PIsberg/codekarta)

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
</div>

## 🗺️ Interactive Diagram Viewer

To navigate large or highly coupled class systems easily, code-karta includes a self-contained, high-fidelity **Interactive Diagram Viewer** at [`docs/diagrams/viewer.html`](docs/diagrams/viewer.html).

Simply open [`viewer.html`](docs/diagrams/viewer.html) in any modern web browser to access premium exploration controls:
*   🔍 **Real-Time Search & Highlight:** Highlight matching classes, methods, or packages; non-matching elements are automatically dimmed to $15\%$ opacity to isolate dependencies.
*   ⚙️ **Relationship Toggles:** Hide or show specific edge types (e.g. Method Calls, Composition, Exceptions, or Inheritance) with simple checkable switches to isolate structural concerns.
*   🔬 **Responsive Zoom & Pan:** Use your mouse wheel, touchpad, or HUD buttons for fluid zoom and click-and-drag panning.
*   🔄 **Tab Switcher:** Switch between all six system diagrams seamlessly in a single page.

## Code Quality

Every `mvn verify` runs the full quality gate: PMD + CPD ([`pmd-ruleset.xml`](pmd-ruleset.xml)), SpotBugs ([`spotbugs-exclude.xml`](spotbugs-exclude.xml) documents the intentional patterns), and the ArchUnit fitness functions in [`ArchitectureRulesTest`](code-karta-cli/src/test/java/com/karta/cli/ArchitectureRulesTest.java) that enforce the 3-tier architecture. All main packages are [JSpecify](https://jspecify.dev/) `@NullMarked`.

Mutation testing (PIT) is opt-in:

```bash
mvn -B -Pmutation test-compile org.pitest:pitest-maven:mutationCoverage
```

## Modules

| Module | Purpose |
|---|---|
| `code-karta-core` | Shared graph IR |
| `code-karta-input` | JavaParser-based source analysis |
| `code-karta-layout` | Simple and ELK layout engines |
| `code-karta-render` | SVG rendering |
| `code-karta-cli` | Command-line wrapper |

Agents and contributors can use [`docs/SKILL.md`](docs/SKILL.md) as a compact operating guide for using and extending the library.
