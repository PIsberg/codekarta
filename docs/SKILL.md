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
java -jar code-karta-cli/target/code-karta-cli-1.0-SNAPSHOT-all.jar \
  --input <path> \
  --output <dir> \
  [--sequence-only] \
  [--state-machine] \
  [--layout simple|elk] \
  [--exclude <patterns>] \
  [--max-depth <depth>]
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

Use `--layout elk` for large graphs. Use `--sequence-only` when exception propagation and try/catch regions are noise.
Use `--state-machine` for enum-backed workflow code where enum constants represent states and switch assignments or `transition(from, to, event)` calls represent transitions.
Use `--exclude <patterns>` (comma-separated wildcards, e.g. `*Test,se.deversity.codekarta.util.*,Map`) to filter noisy types/methods under scale.
Use `--max-depth <depth>` (integer) to limit call-sequence stitching hierarchy.

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

For docs-only changes, at least verify the changed Markdown for obvious command drift against `KartaCli.printUsage()` and `docs/ARCHITECTURE.md`.

When changing CLI behavior, update:

- `code-karta-cli/src/main/java/com/karta/cli/KartaCli.java`
- `code-karta-cli/src/test/java/com/karta/cli/KartaCliTest.java`
- `docs/README.md`
- `docs/ARCHITECTURE.md`
- this `SKILL.md`
