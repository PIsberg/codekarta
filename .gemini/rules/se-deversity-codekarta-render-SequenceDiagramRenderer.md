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

### Rules for method orderMessages
- **Rule**: Free to change, but every mirror must change in the same commit.
- **Mirrors**: se.deversity.codekarta.input.MultiFileSequenceParser#pruneToMaxDepth
- **Reason**: Both decide where a call tree starts: a METHOD no other method calls (self-calls do not count), then, for a cycle nothing outside calls, its first method in graph order. The tiers cannot share code (input and render may not reference each other), so they are two copies; if they disagree, --max-depth keeps a tree the renderer never draws, or the reverse (issue #229).
- **Enforced by**: SequenceDiagramRendererTest.aRecursiveEntryMethodStillStartsItsCallTree / aCycleWithNoOutsideCallerIsDrawnFromItsFirstMethod, and the MultiFileSequenceParserTest maxDepth twins
<!-- VIBETAGS-END -->
