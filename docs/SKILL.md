# code-karta Skill

Use this skill when you need to generate, inspect, or extend Java architecture diagrams with code-karta.

## What code-karta Does

code-karta converts Java source into SVG diagrams through a three-stage pipeline:

1. `code-karta-input` parses Java source into a `Graph`.
2. `code-karta-layout` assigns coordinates to that graph.
3. `code-karta-render` renders the graph as SVG.

The shared type is `se.deversity.codekarta.core.model.Graph`. Keep parser, layout, and rendering responsibilities separate.

## Fast CLI Usage

Build the CLI:

```bash
mvn clean package
```

Run it:

```bash
java -jar code-karta-cli/target/code-karta-cli-0.2.0-all.jar \
  --input <path> \
  --output <dir> \
  [--sequence-only] \
  [--state-machine] \
  [--layout simple|elk] \
  [--exclude <patterns>] \
  [--max-depth <depth>] \
  [--max-members <n>] \
  [--modules-only] \
  [--split-packages] \
  [--output-name <file>]
```

Input path decides the diagram:

| Input | Output |
|---|---|
| `module-info.java` | `module-diagram.svg` |
| directory | `class-diagram.svg` |
| single `.java` file | `<classname>-sequence-diagram.svg` with exception-flow analysis |
| single `.java` file plus `--sequence-only` | call-only sequence diagram |
| directory plus `--sequence-only` | `sequence-diagram.svg` stitched across files |
| file or directory plus `--state-machine` | state transition diagram |
| directory plus `--modules-only` | `modules-diagram.svg` from `module-info.java`, or from the Maven/Gradle reactor when the project declares no JPMS modules |

Use `--layout elk` for large graphs. Use `--sequence-only` when exception propagation and try/catch regions are noise.
Use `--state-machine` for enum-backed workflow code where enum constants represent states and switch assignments or `transition(from, to, event)` calls represent transitions.
Use `--exclude <patterns>` (comma-separated wildcards, e.g. `*Test,se.deversity.codekarta.util.*,Map`) to filter noisy types/methods under scale.
Use `--max-depth <depth>` (integer) to limit call-sequence stitching hierarchy.
Use `--output-name <file>` to write several diagrams into one directory — the derived names (`class-diagram.svg` and friends) otherwise collide, forcing one output directory per diagram.
Use `--max-members <n>` (or `all`) to raise or disable the six-member cap on class compartments; the default suits a large package, not a five-class one.

## Library Usage

Use the facade for normal parsing:

```java
Path input = Path.of("src/main/java/com/example/domain");
Graph graph = new JavaSourceInputParser().parse(input);
new SimpleLayoutEngine().layout(graph);
String svg = new SvgRenderer().render(graph);
```

Use ELK layout for larger graphs:

```java
new ElkLayoutEngine().layout(graph);
```

Use call-only parsing for one file:

```java
Graph graph = new JavaSourceInputParser(true).parse(Path.of("src/main/java/com/example/Service.java"));
```

Use stitched multi-file sequence parsing:

```java
Path sourceRoot = Path.of("src/main/java");
Graph graph = new MultiFileSequenceParser().parse(sourceRoot);
new ElkLayoutEngine().layout(graph);
String svg = new SvgRenderer().render(graph);
```

Pass the source root, not a deeply nested package, when cross-package symbol resolution matters.

Use state-machine parsing:

```java
Graph graph = new StateMachineParser().parse(Path.of("src/main/java/com/example/Workflow.java"));
new SimpleLayoutEngine().layout(graph);
String svg = new SvgRenderer().render(graph);
```

## Extension Rules

Preserve the project boundaries:

| Change | Where it belongs |
|---|---|
| New Java analysis or diagram semantics | `code-karta-input` |
| New graph data shape | `code-karta-core` |
| New coordinate algorithm | `code-karta-layout` |
| New SVG appearance or output format | `code-karta-render` |
| New CLI flag or output naming behavior | `code-karta-cli` |

Do not make input depend on layout or render. Do not make render depend on parsers. The graph IR is the only object that should cross tiers.

## Parser Selection

`JavaSourceInputParser` dispatches by path:

```text
module-info.java -> ModuleInfoParser
directory        -> ClassDiagramParser
.java file       -> ExceptionFlowParser
.java + sequence -> CallSequenceParser
```

The CLI handles directory plus `--sequence-only` separately with `MultiFileSequenceParser`, and file/directory plus `--state-machine` with `StateMachineParser`.

## Output and Theming

SVG output is self-contained. For custom styling:

```java
String svg = new SvgRenderer().render(graph, cssOverride);
```

Target stable CSS classes such as `.node-rect`, `.node-label`, `.edge-line`, and `.group-rect`.

## Testing Guidance

Run the full test suite after behavioral changes:

```bash
mvn test
```

For docs-only changes, at least verify the changed Markdown for obvious command drift against `KartaCli.printUsage()` and `docs/CLI.md`.

When changing CLI behavior, update all of:

- `code-karta-cli/src/main/java/se/deversity/codekarta/cli/KartaCli.java` — including `printUsage()`
- `code-karta-cli/src/main/java/se/deversity/codekarta/cli/RunOptions.java`
- `code-karta-cli/src/test/java/se/deversity/codekarta/cli/KartaCliTest.java`
- `docs/CLI.md` — the flag table
- `docs/DIAGRAM-MODES.md` — if the change affects which diagram an input produces
- this `SKILL.md` — the flag list above

`RunOptions` carries an `@AIKeepInSync` guardrail naming those mirrors, because nothing in the
build catches a flag added to one and not the others.

## Guardrails

Source annotations generate the `<project_guardrails>` blocks in `CLAUDE.md` and `llms.txt` at
compile time. Never hand-edit inside the `VIBETAGS-START` / `VIBETAGS-END` markers, and never put
a VibeTags annotation on a test class — see `docs/VIBETAGS.md`.
