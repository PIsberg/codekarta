---
paths: ["**/BuildReactorParser.java"]
---

<!-- VIBETAGS-START -->
# Rules for BuildReactorParser

## Context & Focus
- **Focus**: Reactor membership comes from the build files, never from the directory layout: a directory holding a pom.xml is not a module unless some aggregator lists it. Only intra-reactor dependencies become REQUIRES edges.
- **Avoid**: Resolving property placeholders, profiles, or dependencyManagement — this is a structural read, not a build. Following <module> or include() paths outside the reactor root.

## Strict Test Isolation
- **Rule**: Strict test isolation required. AI-generated or modified tests must not share mutable state, rely on execution order, or conflict on external resources.
- **Reason**: BuildReactorParserTest writes a synthetic reactor into its own @TempDir rather than reading this repository's build files — the parser's whole job is reading build files, so a shared or real fixture would couple cases to each other and to unrelated build changes.

## Architectural Boundary Constraints
- **Layer**: input
- **Prohibited References**: layout, render, cli
<!-- VIBETAGS-END -->
