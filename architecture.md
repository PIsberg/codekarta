# Architecture

> *This document is regenerated automatically by the build.*  
> Each section uses a different diagram type that the tool can produce.
>
> **Regenerate:** `mvn verify -DskipTests` (Maven) · `./gradlew :code-karta-cli:generateDiagrams` (Gradle)  
> **Skip generation:** add `-DskipDiagrams=true` (Maven) · `-PskipDiagrams` (Gradle)

---

## Overview

code-karta is a three-tier pipeline that turns Java source into SVG diagrams:

```
code-karta-core       ← pure IR data model (Graph, Node, Edge, Group)
      ↑         ↑         ↑
code-karta-input  code-karta-layout  code-karta-render
                                           ↑
                                     code-karta-cli   ← wires all three
```

No tier may import from a tier beside or below it. The `Graph` object is the only thing that passes between tiers.

---

## 1. Core IR Model — Class Diagram

Generated from `code-karta-core/src/main/java/com/karta/core/model`:

```
java -jar karta.jar --input code-karta-core/src/main/java/com/karta/core/model --output docs/diagrams
```

The IR carries just enough structure to describe any diagram type. `NodeDimensions` is the single source of truth for default node sizing across all tiers.

![Core IR class diagram](docs/diagrams/class-diagram.svg)

---

## 2. Example Module Structure — Module Diagram

Generated from `example-shipping-system/src/main/java/module-info.java`:

```
java -jar karta.jar --input example-shipping-system/src/main/java/module-info.java --output docs/diagrams
```

The shipping system fixture declares its module dependencies (JPMS `requires`/`exports`). The `MODULE` and `PACKAGE` nodes and `REQUIRES`/`EXPORTS` edges are extracted directly from the module descriptor.

![Module diagram](docs/diagrams/module-diagram.svg)

---

## 3. CLI Entry Point — Exception-Flow Sequence Diagram

Generated from `KartaCli.java` (default mode — includes exception propagation edges):

```
java -jar karta.jar --input code-karta-cli/src/main/java/com/karta/cli/KartaCli.java --output docs/diagrams
```

`KartaCli` is the pipeline entry point. This diagram shows its method-call sequence with try/catch boundaries surfaced as `Group` nodes and `EXCEPTION_PROPAGATION` edges marking which methods declare checked exceptions. Requires `LanguageLevel.JAVA_17` in JavaParser to parse the arrow-switch dispatch in `main()`.

![KartaCli exception-flow sequence diagram](docs/diagrams/kartacli-sequence-diagram.svg)

---

## 4. Parser Call Chain — Call-Sequence Diagram (`--sequence-only`)

Generated from `CallSequenceParser.java` with `--sequence-only` (CALLS edges only, no exception flow):

```
java -jar karta.jar --input code-karta-input/src/main/java/com/karta/input/parser/CallSequenceParser.java \
     --output docs/diagrams --sequence-only
```

`--sequence-only` strips out exception-flow analysis and emits only `CALLS` edges with sequence-order labels. Use this for clean call graphs when exception propagation is not relevant.

![CallSequenceParser call-sequence diagram](docs/diagrams/callsequenceparser-sequence-diagram.svg)

---

## 5. Input Layer Call Graph — Multi-File Stitched Sequence

Generated from the full `code-karta-input` source root with `--sequence-only`:

```
java -jar karta.jar --input code-karta-input/src/main/java/com/karta/input \
     --output docs/diagrams --sequence-only
```

When `--input` is a directory combined with `--sequence-only`, all `.java` files are parsed together. Cross-file method calls are resolved via JavaParser's symbol solver (`JavaParserTypeSolver` anchored at the source root), and callee nodes use the qualified `ClassName.method` form so calls that cross file boundaries are stitched into a single unified graph. The `--layout elk` flag applies ELK's Sugiyama pipeline, reducing the diagram width from ~13 000 px to ~2 500 px compared with the BFS grid.

> **Tip:** For accurate cross-package resolution, point `--input` at the package root (e.g. `src/main/java`) rather than a nested subdirectory.

![Input layer multi-file stitched sequence diagram](docs/diagrams/sequence-diagram.svg)

---

## Layout Engines

Two layout engines implement the `LayoutEngine` interface. The engine is selected with `--layout`:

| Flag | Engine | Algorithm |
|------|--------|-----------|
| `--layout simple` (default) | `SimpleLayoutEngine` | BFS from root nodes → depth layers → row/column grid |
| `--layout elk` | `ElkLayoutEngine` | Eclipse Layout Kernel — Sugiyama pipeline (layer assignment, crossing minimisation, orthogonal edge routing). Falls back to `simple` on failure. |

ELK is recommended for large graphs where the BFS grid produces overly wide diagrams.

---

## Diagram Type Summary

| Input | Diagram type | Output filename |
|-------|--------------|-----------------|
| `module-info.java` | Module diagram | `module-diagram.svg` |
| Directory (default) | Class diagram | `class-diagram.svg` |
| `*.java` file (default) | Exception-flow sequence | `<name>-sequence-diagram.svg` |
| `*.java` file + `--sequence-only` | Call sequence | `<name>-sequence-diagram.svg` |
| Directory + `--sequence-only` | Multi-file stitched sequence | `sequence-diagram.svg` |
