# PLAN.md — code-karta Implementation Plan

## Reasoning & Key Decisions

### Java Version
Using Java 21 (LTS) as the compilation target. The spec states JDK 24+ as a project goal; however, Java 21 is the current LTS, widely available, and provides all necessary language features (records, pattern matching, text blocks, sealed types). Bump to 24 when the need arises.

### Layout Engine
The spec lists `guru.nidi:graphviz-java` or ELK as layout options. Both require native binaries or complex OSGi setups that break on clean `mvn clean compile`. Instead, `SimpleLayoutEngine` implements a pure-Java BFS hierarchical layout (topological sort → row/column placement). The `LayoutEngine` interface allows plugging in ELK/Graphviz later without touching other tiers.

### Logging
Using `java.util.logging.Logger` (built-in, zero deps) instead of SLF4J to avoid binding dependencies across modules. Fault-tolerance guardrail: every parser wraps parse exceptions in try/catch, logs a warning, and returns a partial graph.

### Immutability vs Mutability
The spec calls the Core IR "immutable." In practice, the layout phase needs to set coordinates on Node objects. Using mutable POJOs (with setters) is the pragmatic choice. Immutability is enforced architecturally: the input parsers never call layout code; the renderer never calls parser code.

### Test Scope
Unit tests cover every class with inline synthetic Java source strings via JUnit 5 `@TempDir`. Integration tests in `code-karta-input` verify all three diagram types (module, class, call-sequence) against the `example-shipping-system` fixture.

### Gradle Build (dual-build)
The spec requires both Maven and Gradle. Gradle Kotlin DSL (`.gradle.kts`) is chosen over Groovy DSL for type safety. Shared config (repos, JUnit 5, Java toolchain) lives in the root `build.gradle.kts` via `subprojects {}` using `apply(plugin = "java")`; each module's own `build.gradle.kts` only declares module-specific dependencies. The Gradle wrapper is generated via `gradle wrapper --gradle-version 9.5.1` so the build is self-contained.

---

## Module Dependency Graph

```
code-karta-core       (no internal deps — pure data model)
      ↑         ↑         ↑
code-karta-input  code-karta-layout  code-karta-render
```

---

## File Plan

### Root
- `pom.xml` — Maven reactor, dependency management (Jackson 2.17, JavaParser 3.25, JUnit 5.11, Java 21)
- `settings.gradle.kts` — Gradle settings: project name + subproject includes
- `build.gradle.kts` — shared Gradle config for all subprojects (toolchain, repos, JUnit)

### code-karta-core
- `build.gradle.kts` — Jackson dependency
- `model/Node.java` — id, type, label, properties, x?, y?, width?, height?
- `model/Edge.java` — id, sourceId, targetId, type, label?
- `model/Group.java` — id, label, memberIds[], properties
- `model/Graph.java` — nodes[], edges[], groups[]; helpers: addNode, addNodeIfAbsent, addEdge, addGroup, findNode
- Tests: NodeTest, EdgeTest, GroupTest, GraphTest

### code-karta-input
- `build.gradle.kts` — core + javaparser dependencies
- `InputParser.java` — `Graph parse(Path path)` interface
- `JavaSourceInputParser.java` — facade: delegates to specific parsers based on path
- `parser/ModuleInfoParser.java` — parses module-info.java → MODULE nodes, REQUIRES/EXPORTS edges
- `parser/ClassDiagramParser.java` — walks dir → CLASS/INTERFACE nodes, EXTENDS/IMPLEMENTS/HAS edges (skips primitives/stdlib types)
- `parser/CallSequenceParser.java` — VoidVisitorAdapter → METHOD nodes, CALLS edges with order label
- Tests: ModuleInfoParserTest, ClassDiagramParserTest, CallSequenceParserTest

### code-karta-layout
- `build.gradle.kts` — core dependency
- `LayoutEngine.java` — `Graph layout(Graph graph)` interface
- `SimpleLayoutEngine.java` — BFS level assignment → row/column coordinates (150×50 per node, 50h/80v spacing)
- Tests: SimpleLayoutEngineTest

### code-karta-render
- `build.gradle.kts` — core dependency
- `SvgRenderer.java` — pure Java SVG generation: rect+text per node, line per edge, dashed rect per group, injectable CSS
- Tests: SvgRendererTest

### code-karta-cli
- `pom.xml` — depends on all four modules; `maven-shade-plugin` produces `*-all.jar` fat JAR; `exec-maven-plugin` enables `mvn exec:java`
- `build.gradle.kts` — `application` plugin (for `run` task) + custom `fatJar` task for self-contained JAR
- `KartaCli.java` — `main()` (arg parsing + error handling) + `run(inputPath, outputDir)` (testable pipeline entry point) + `deriveOutputName()` (input → SVG filename)
- Tests: KartaCliTest — tests `run()` and `deriveOutputName()` via `@TempDir`; no `System.exit` in tests

### docs/
- `README.md` — prerequisites, build commands, CLI usage, diagram types, output location
- `ARCHITECTURE.md` — pipeline overview, module responsibilities, IR schema, extension points

---

## Implementation Sequence

1. Root pom.xml + module pom.xml files
2. Core model classes + tests
3. Input parser classes + tests
4. Layout classes + tests
5. Render class + tests
6. `mvn clean compile` → fix any compilation errors → iterate
7. Root settings.gradle.kts + build.gradle.kts + module build.gradle.kts files
8. `gradle wrapper --gradle-version 9.5.1` → generate self-contained wrapper
9. `.\gradlew.bat test` → fix any Gradle-specific issues → iterate
10. code-karta-cli module (KartaCli + tests) + fat JAR packaging in both build systems
11. example-shipping-system reorganised to `src/main/java/` layout + standalone pom.xml + build.gradle.kts
12. SVG diagrams generated to `example-shipping-system/diagrams/` (module, class, sequence)
13. docs/README.md + docs/ARCHITECTURE.md — full pipeline doc including output location and pre-generated diagram links
