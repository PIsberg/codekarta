# CLI Reference

```bash
java -jar code-karta-cli/target/code-karta-cli-0.2.0-all.jar \
  --input <path> \
  --output <dir> \
  [--output-name <file>] \
  [--sequence-only] \
  [--state-machine] \
  [--modules-only] \
  [--split-packages] \
  [--layout simple|elk] \
  [--exclude <patterns>] \
  [--max-depth <depth>] \
  [--max-members <n>]
```

## Flags

| Flag | Default | Description |
|---|---|---|
| `--input <path>` | required | Java source path to parse. The path determines the diagram type. |
| `--output <dir>` | `./output` | Directory where the SVG file is written. Created if missing. |
| `--output-name <file>` | derived | File name to write inside `--output`, instead of the name derived from the input (`class-diagram.svg` and friends). Lets several runs share one output directory. Must be a plain file name — a name with separators or `..` is refused. |
| `--sequence-only` | off | Emits only `CALLS` edges. For directory input, parses all Java files together into one stitched sequence graph. |
| `--state-machine` | off | Emits `STATE` nodes and `TRANSITION` edges from enum constants, switch cases, state assignments, and `transition(from, to, event)` calls. |
| `--modules-only` | off | Cross-module diagram. Reads `module-info.java` when the project declares JPMS modules, and otherwise the build reactor — Maven `<modules>` or Gradle `include(...)` — with `HAS` for aggregation and `REQUIRES` for intra-reactor dependencies. |
| `--split-packages` | off | Emits one diagram per package instead of one for the whole tree, mirroring the package structure under `--output`. |
| `--layout simple\|elk` | `simple` | `simple` is the pure-Java BFS grid. `elk` uses Eclipse Layout Kernel layered layout and is better for larger graphs. |
| `--exclude <patterns>` | off | Comma-separated wildcard patterns of classes or methods to exclude (e.g. `*Test,se.deversity.codekarta.util.*,Map`) to reduce visual clutter. |
| `--max-depth <depth>` | off | Maximum call sequence depth to parse or stitch. |
| `--max-members <n>` | `6` | Field/method lines per class box before the rest collapse into `…(+N more)`. Pass `all` or `0` to show every member. |
| `--help` | off | Prints CLI usage. |

The flag set here, `KartaCli.printUsage()`, and the `RunOptions` record are three copies of the
same list. `RunOptions` carries an `@AIKeepInSync` guardrail naming the other two, because
nothing in the build catches a flag that was added to one and not the others.

## Choosing flags

- `--layout elk` for large graphs; the simple grid gets very wide.
- `--sequence-only` when exception propagation and try/catch regions are noise.
- `--state-machine` for enum-backed workflow code.
- `--exclude` to drop noisy types and methods at scale.
- `--output-name` to write several diagrams into one directory — derived names otherwise collide,
  forcing one output directory per diagram.
- `--max-members` to raise or disable the six-member compartment cap; the default suits a diagram
  of a large package, not one of five classes.

## Development invocation

Running from source instead of a built JAR: see [`BUILD.md`](BUILD.md).

## Output

SVG files are written to `--output`, or to `./output` when `--output` is omitted.

```text
output/
├── module-diagram.svg
├── class-diagram.svg
├── kartacli-sequence-diagram.svg
├── kartacli-state-machine-diagram.svg
└── sequence-diagram.svg
```

The SVGs are self-contained and open directly in a browser or IDE. Rendered elements use stable
CSS classes such as `.node-rect`, `.edge-line`, and `.group-rect`; library users can pass a custom
stylesheet to `SvgRenderer.render(graph, css)` — see [`LIBRARY.md`](LIBRARY.md).

Recommended local ignore rules:

```gitignore
output/
target/diagrams/
build/diagrams/
```
