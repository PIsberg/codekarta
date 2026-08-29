# Compatibility and Versioning Policy

This page states what a consumer can depend on, so that upgrading code-karta is a decision you can
make from the version number rather than from reading a diff.

code-karta follows [Semantic Versioning 2.0.0](https://semver.org/spec/v2.0.0.html).

## While the version is 0.x

The project is pre-1.0. Under semantic versioning that permits a breaking change in any minor
release, and this project uses that latitude. In practice:

- **0.x.y to 0.x.(y+1)** (patch): no breaking change to anything in the public surface below.
- **0.x to 0.(x+1)** (minor): may break the public surface. Every break is listed in
  `CHANGELOG.md` under a `### Changed` or `### Removed` heading with the migration.

Once 1.0.0 ships, a break in the public surface will require a major version.

## What is public

These are the things a break in which counts as a breaking change.

| Surface | Where | Notes |
|---|---|---|
| `se.deversity.codekarta.core.model` | `code-karta-core` | The `Graph` IR: `Graph`, `Node`, `Edge`, `Group`, `NodeType`, `EdgeType`, `NodeDimensions`. This is the contract every tier speaks. |
| `NodeType` and `EdgeType` string values | `code-karta-core` | Matched by identity in `SvgRenderer` and in every parser. The strings, not just the constant names, are the contract. |
| `InputParser` and its implementations | `code-karta-input` | `parse(Path)` signature and the fault-tolerance contract. |
| `LayoutEngine` and its implementations | `code-karta-layout` | Including the in-place mutation contract and the null-coordinate convention. |
| `SvgRenderer.render(Graph)` and `render(Graph, String)` | `code-karta-render` | |
| `JsonRenderer.render(Graph)` and the JSON it produces | `code-karta-render` | The schema is the IR field names, already listed above. Indentation and key order are stable but are not themselves a contract. |
| CSS class names in the emitted SVG | `code-karta-render` | `.node-rect`, `.node-label`, `.edge-line`, `.edge-label`, `.group-rect`. Consumers theme against these. |
| CLI flags | `code-karta-cli` | Documented in [`docs/CLI.md`](docs/CLI.md). Removing or repurposing one is breaking. Adding one is not. |
| CLI exit codes | `code-karta-cli` | `0` success, `1` usage error, `2` runtime failure. |
| `KartaCli.run`, `KartaCli.runPerPackage`, `RunOptions` | `code-karta-cli` | The programmatic entry points. Public because the Maven plugin drives them: a capability reachable only through `main(String[])` is one an embedding caller has to shell out to. |
| Plugin goal, parameters and user properties | `code-karta-maven-plugin` | `karta:generate` and the parameter names in [`docs/MAVEN-PLUGIN.md`](docs/MAVEN-PLUGIN.md). `PluginDescriptorTest` fails the build when the descriptor and that table disagree. |
| Maven coordinates | | `se.deversity.codekarta:code-karta-{core,input,layout,render,cli,maven-plugin}` |

## What is not public

Depending on any of the following is depending on an implementation detail. It can change in a
patch release.

- Anything in a package containing `.internal.`
- Package-private and protected members, and any class not listed above
- The exact SVG byte output. Geometry, element ordering, whitespace and `id` attribute values are
  free to change. Test against the CSS classes and the graph, not against a golden SVG string.
- Log messages, their text, and their level
- Transitive dependency versions. JavaParser, ELK, Jackson and Xtext are upgraded freely, including
  across their own major versions.

## Java version

| Artifact | Compiled for | Runs on |
|---|---|---|
| `code-karta-core` | Java 17 | Java 17 and newer |
| `code-karta-input` | Java 17 | Java 17 and newer |
| `code-karta-layout` | Java 17 | Java 17 and newer, with one caveat below |
| `code-karta-render` | Java 17 | Java 17 and newer |
| `code-karta-cli` | Java 17 | Java 17 and newer, with the same caveat |
| `code-karta-maven-plugin` | Java 17 | Whatever JVM runs Maven, 17 and newer. Maven 3.9.11 or newer, declared in the descriptor as `requiredMavenVersion`. |

Every module targets Java 17, so an application still on 17 can depend on the library and run the
CLI jar. Verified rather than assumed: CI compiles a consumer with JDK 17 and runs it on JDK 17,
runs the shipped fat jar on JDK 17, and asserts every jar carries class file major version 61.

**`ElkLayoutEngine` needs a Java 21 runtime.** ELK itself is built for 17, but it resolves its
layout algorithms through `ServiceLoader`, and one transitive dependency,
`org.eclipse.xtext.xbase.lib`, is compiled for Java 21 in every published version including
2.43.0 and 2.44.0. On a 17 runtime the service lookup therefore fails. `ElkLayoutEngine` catches
that, logs a warning and falls back to `SimpleLayoutEngine`, so a Java 17 consumer still gets a
laid-out graph; it gets the BFS grid rather than the layered algorithm. On the CLI that means
`--layout elk` on a Java 17 runtime produces the same diagram `--layout simple` would, and logs a
warning saying so. Everything else, parsing, `SimpleLayoutEngine`, SVG and JSON output, works
unchanged on 17.

Building code-karta from source requires a JDK between 21 and 25 regardless. Not because any
shipped artifact targets it, since they all target 17, but because the test sources compile at 21
and JaCoCo cannot instrument class files from a much newer JDK. The enforcer checks the range and
fails with a message naming the toolchain rather than letting it surface as an instrumentation
error that reads like a defect here.

Raising the floor of a published artifact is a breaking change and gets a minor version bump before
1.0, a major version after.

## Deprecation

A public element that is going away is annotated `@Deprecated(since = "<version>", forRemoval =
true)`, kept for at least one minor release, and its removal is announced in `CHANGELOG.md` in the
release that deprecates it. There is no removal without a preceding deprecated release.

## Supported releases

Fixes, including security fixes, land on the latest minor release. There is no maintenance branch
for older minors. See [`SECURITY.md`](SECURITY.md).

## Reproducibility

`KartaCli.run` is idempotent: identical inputs produce byte-identical SVG. CI enforces this on every
push by regenerating every committed diagram and failing on any diff. Downstream repositories that
commit generated diagrams can rely on this within a single code-karta version. Across versions the
bytes may change; see "What is not public" above.
