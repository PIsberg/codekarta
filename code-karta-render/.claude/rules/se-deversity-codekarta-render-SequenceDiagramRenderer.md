---
paths: ["**/SequenceDiagramRenderer.java"]
---

<!-- VIBETAGS-START -->
# Rules for SequenceDiagramRenderer

## Context & Focus
- **Focus**: Participants are derived from METHOD node-id prefixes (before last dot). Messages are DFS-ordered by integer CALLS label from entry methods (no incoming CALLS). EXCEPTION nodes (id prefix 'exception:') are pinned last. Groups become UML region frames spanning the Y-range of their member messages.
- **Avoid**: Reading Node.x/y from the Graph — this renderer ignores BFS coordinates entirely and computes its own lane geometry from LANE_W and participant index.

## Strict Test Isolation
- **Rule**: Strict test isolation required. AI-generated or modified tests must not share mutable state, rely on execution order, or conflict on external resources.

## Architectural Boundary Constraints
- **Layer**: render
- **Prohibited References**: input, layout, cli
<!-- VIBETAGS-END -->
