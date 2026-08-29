<!-- VIBETAGS-START -->
# Rules for SequenceDiagramRenderer

## Context & Focus
- **Focus**: Participants are derived from METHOD node-id prefixes (before last dot). Messages are DFS-ordered by integer CALLS label from entry methods (no incoming CALLS). EXCEPTION nodes (id prefix 'exception:') are pinned last. Groups become UML region frames spanning the Y-range of their member messages.
- **Avoid**: Reading Node.x/y from the Graph — this renderer ignores BFS coordinates entirely and computes its own lane geometry from LANE_W and participant index.

## Strict Test Isolation
- **Rule**: Strict test isolation required. AI-generated or modified tests must not share mutable state, rely on execution order, or conflict on external resources.
- **Reason**: SequenceDiagramRendererTest constructs a fresh Graph per case and asserts on returned SVG strings. No shared fixture, no temp files, no fixed ports — keep it that way so the suite stays safe under Surefire forkCount > 1.

## Architectural Boundary Constraints
- **Layer**: render
- **Prohibited References**: input, layout, cli
<!-- VIBETAGS-END -->
