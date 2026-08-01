# Library Use

The CLI is a thin wrapper. Java code can drive the three tiers directly — parse, lay out, render.

```java
Path input = Path.of("src/main/java");
Graph graph = new JavaSourceInputParser().parse(input);
new ElkLayoutEngine().layout(graph);
String svg = new SvgRenderer().render(graph);
```

## Parsers

| Call | Produces |
|---|---|
| `new JavaSourceInputParser().parse(path)` | Auto-detects the input type and delegates. The normal entry point. |
| `new JavaSourceInputParser(true).parse(file)` | Call-only single-file parsing (no exception flow). |
| `new MultiFileSequenceParser().parse(sourceRoot)` | Stitched cross-file sequence graph. Pass the source root, not a nested package, when cross-package symbol resolution matters. |
| `new StateMachineParser().parse(path)` | Enum-backed state transition graph. |

`JavaSourceInputParser` dispatches by path shape:

```text
module-info.java -> ModuleInfoParser
directory        -> ClassDiagramParser
.java file       -> ExceptionFlowParser
.java + sequence -> CallSequenceParser
```

Directory plus `--sequence-only`, and the `--state-machine` mode, are wired by the CLI rather than
by the dispatcher — `MultiFileSequenceParser` and `StateMachineParser` are called directly.

**Every parser is fault-tolerant by contract**: `parse(Path)` never throws. Failures are logged and
a partial — possibly empty — `Graph` comes back, so the pipeline always produces some output.

## Layout

Both engines implement `LayoutEngine` and mutate the graph in place, returning the same instance:

```java
new SimpleLayoutEngine().layout(graph);   // BFS grid, pure Java
new ElkLayoutEngine().layout(graph);      // ELK layered, better for large graphs
```

Nodes whose position cannot be resolved are left at `null` rather than defaulted — the renderer
skips them. That pairing is deliberate and load-bearing on both sides.

## Rendering and theming

```java
String svg = new SvgRenderer().render(graph);
String themed = new SvgRenderer().render(graph, cssOverride);
```

Target the stable CSS classes: `.node-rect`, `.node-label`, `.edge-line`, `.edge-label`,
`.group-rect`. These names are a published contract — renaming one breaks every existing
stylesheet.

`SvgRenderer` routes interaction graphs (METHOD nodes with integer `CALLS` labels) to
`SequenceDiagramRenderer` automatically. Detection is content-based; there is no diagram-type field
on `Graph`.

## Where a change belongs

| Change | Module |
|---|---|
| New Java analysis or diagram semantics | `code-karta-input` |
| New graph data shape | `code-karta-core` |
| New coordinate algorithm | `code-karta-layout` |
| New SVG appearance or output format | `code-karta-render` |
| New CLI flag or output naming behavior | `code-karta-cli` |

The graph IR is the only object that crosses tiers. Input must not depend on layout or render;
render must not depend on parsers. [`ARCHITECTURE.md`](ARCHITECTURE.md) has the full rule set, and
`ArchitectureRulesTest` enforces it with ArchUnit.
