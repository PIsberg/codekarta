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
| CSS class names in the emitted SVG | `code-karta-render` | `.node-rect`, `.node-label`, `.edge-line`, `.edge-label`, `.group-rect`. Consumers theme against these. |
| CLI flags | `code-karta-cli` | Documented in [`docs/CLI.md`](docs/CLI.md). Removing or repurposing one is breaking. Adding one is not. |
| CLI exit codes | `code-karta-cli` | `0` success, `1` usage error, `2` runtime failure. |
| Maven coordinates | | `se.deversity.codekarta:code-karta-{core,input,layout,render,cli}` |

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
| `code-karta-core` | Java 21 | Java 21 and newer |
| `code-karta-input` | Java 21 | Java 21 and newer |
| `code-karta-layout` | Java 21 | Java 21 and newer |
| `code-karta-render` | Java 21 | Java 21 and newer |
| `code-karta-cli` | Java 21 | Java 21 and newer |

Building code-karta from source requires JDK 21.

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
