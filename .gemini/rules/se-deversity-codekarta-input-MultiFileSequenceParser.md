<!-- VIBETAGS-START -->
# Rules for MultiFileSequenceParser

## Context & Focus
- **Focus**: Cross-file call resolution via JavaSymbolSolver: resolved callees get 'ClassName.methodName' ids (MethodIds adds a parameter-type suffix for overloads) so nodes from different source files are automatically linked. Unresolvable calls fall back to scope-based naming without crashing.
- **Avoid**: Adding exception-flow parsing here — catch-boundary groups and EXCEPTION_PROPAGATION edges belong to ExceptionFlowParser on individual files, not to the multi-file stitching pass.

## Architectural Boundary Constraints
- **Layer**: input
- **Prohibited References**: layout, render, cli

### Rules for method pruneToMaxDepth
- **Rule**: Free to change, but every mirror must change in the same commit.
- **Mirrors**: se.deversity.codekarta.render.SequenceDiagramRenderer#orderMessages
- **Reason**: Both decide where a call tree starts: a METHOD no other method calls (self-calls do not count), then, for a cycle nothing outside calls, its first method in graph order. The tiers cannot share code (input and render may not reference each other), so they are two copies; if they disagree, --max-depth keeps a tree the renderer never draws, or the reverse (issue #229).
- **Enforced by**: MultiFileSequenceParserTest.maxDepthKeepsTheCallTreeOfARecursiveEntryMethod / maxDepthCountsFromTheFirstMethodOfACycleWithNoOutsideCaller, and the SequenceDiagramRendererTest twins
<!-- VIBETAGS-END -->
