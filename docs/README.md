# code-karta Documentation

code-karta turns Java source into SVG architecture diagrams: JPMS modules, class relationships,
single-file call flows, exception propagation, multi-file call sequences, and enum-backed state
transitions.

The project is intentionally small and compiler-like:

1. Parse Java source into a shared graph IR.
2. Assign coordinates with a layout engine.
3. Render the graph as SVG.

## Start here

| Page | What it covers |
|---|---|
| [`BUILD.md`](BUILD.md) | Requirements, Maven and Gradle commands, running from source, diagram regeneration |
| [`CLI.md`](CLI.md) | Every flag, what it defaults to, and how to choose between them |
| [`DIAGRAM-MODES.md`](DIAGRAM-MODES.md) | What each input shape produces, with a worked command per mode |
| [`DIAGRAM-GALLERY.md`](DIAGRAM-GALLERY.md) | The six diagrams code-karta generates from its own source |
| [`LIBRARY.md`](LIBRARY.md) | Using the tiers directly from Java, theming, and where a change belongs |
| [`VIEWER.md`](VIEWER.md) | The self-contained interactive diagram viewer |

## Going deeper

| Page | What it covers |
|---|---|
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | Module responsibilities, graph schema, rendering rules, extension points |
| [`EXAMPLE-PROJECT.md`](EXAMPLE-PROJECT.md) | The shipping fixture that the integration tests assert against |
| [`QUALITY.md`](QUALITY.md) | The `mvn verify` gate: Checkstyle, PMD, SpotBugs, Error Prone, ArchUnit, SBOM |
| [`VIBETAGS.md`](VIBETAGS.md) | How the AI guardrails in `CLAUDE.md` are generated, and the rules for changing them |
| [`SKILL.md`](SKILL.md) | Compact operating guide for agents using or extending the library |
| [`SPEC.md`](SPEC.md) | Behaviour specification |
| [`PLAN.md`](PLAN.md) | The original implementation plan and its reasoning |

## One-minute version

```bash
mvn clean package

java -jar code-karta-cli/target/code-karta-cli-0.3.0-all.jar \
  --input example-shipping-system/src/main/java/com/karta/shipping/domain \
  --output output
```

The input path decides the diagram type; flags refine it. `module-info.java` gives a module
diagram, a directory gives a class diagram, a single `.java` file gives an exception-flow sequence
diagram. [`DIAGRAM-MODES.md`](DIAGRAM-MODES.md) has the full table.
