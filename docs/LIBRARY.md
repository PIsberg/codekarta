# Library Use

The CLI is a thin wrapper. Java code can drive the three tiers directly — parse, lay out, render.

Every module targets **Java 17**, so the library can be used from an application that has not
moved to 21. One caveat: `ElkLayoutEngine` needs a Java 21 runtime, because ELK resolves
its algorithms through `ServiceLoader` and `org.eclipse.xtext.xbase.lib` is compiled for 21.
On 17 it logs a warning and falls back to `SimpleLayoutEngine` rather than throwing, so the
pipeline still produces a diagram. [`COMPATIBILITY.md`](../COMPATIBILITY.md) has the detail.

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
new SimpleLayoutEngine().layout(graph);   // BFS grid, pure Java, runs on Java 17
new ElkLayoutEngine().layout(graph);      // ELK layered, better for large graphs, needs Java 21
```

`ElkLayoutEngine.layout` never throws. It catches `Exception`, `LinkageError` and
`ServiceConfigurationError` and falls back to `SimpleLayoutEngine`, because ELK's two realistic
failures are both `Error`s: a missing `ServiceLoader` entry (a shaded jar that did not merge
`META-INF/services`) and a dependency built for a newer JDK than the runtime. Errors outside those
two still propagate; an `OutOfMemoryError` is not a layout problem.

Nodes whose position cannot be resolved are left at `null` rather than defaulted — the renderer
skips them. That pairing is deliberate and load-bearing on both sides.

## Rendering and theming

```java
String svg = new SvgRenderer().render(graph);
String themed = new SvgRenderer().render(graph, cssOverride);
String json = new JsonRenderer().render(graph);   // the graph itself, not a picture of it
```

`JsonRenderer` writes the IR: nodes, edges, groups, and layout coordinates if a layout engine has
run. It round-trips, so `new ObjectMapper().readValue(json, Graph.class)` gives back an equivalent
graph, and its output is byte-identical across runs for the same input. Use it when the consumer is
a tool rather than a person: an architecture rule that should fail a build, a diff between two
revisions, or your own renderer.

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
