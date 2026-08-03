<!-- VIBETAGS-START -->
# Rules for MultiFileSequenceParser

## Context & Focus
- **Focus**: Cross-file call resolution via JavaSymbolSolver: resolved callees get 'ClassName.methodName' ids so nodes from different source files are automatically linked. Unresolvable calls fall back to scope-based naming without crashing.
- **Avoid**: Adding exception-flow parsing here — catch-boundary groups and EXCEPTION_PROPAGATION edges belong to ExceptionFlowParser on individual files, not to the multi-file stitching pass.

## Architectural Boundary Constraints
- **Layer**: input
- **Prohibited References**: layout, render, cli
<!-- VIBETAGS-END -->
