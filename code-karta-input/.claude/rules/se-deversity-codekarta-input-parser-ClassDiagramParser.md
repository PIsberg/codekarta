---
paths: ["**/ClassDiagramParser.java"]
---

<!-- VIBETAGS-START -->
# Rules for ClassDiagramParser

## Context & Focus
- **Focus**: Generic-type stripping: rawType() must be called before SKIP_TYPES lookup so 'List<Node>' → 'List' and gets filtered. Node.properties is populated with truncated field/method summaries for UML compartments. HAS edge labels carry the field name.
- **Avoid**: Bypassing SKIP_TYPES for stdlib types — class diagrams quickly become unreadable with List/Map/String nodes. Populating Node.properties for externally-referenced stub nodes (only populate for types whose source is in this parse run).

## Strict Test Isolation
- **Rule**: Strict test isolation required. AI-generated or modified tests must not share mutable state, rely on execution order, or conflict on external resources.
- **Reason**: ClassDiagramParserTest builds each fixture in its own @TempDir and asserts on the returned Graph. Never reach for a shared static parser or a fixed scratch path — the suite must stay safe under Surefire forkCount > 1.

## Architectural Boundary Constraints
- **Layer**: input
- **Prohibited References**: layout, render, cli
<!-- VIBETAGS-END -->
