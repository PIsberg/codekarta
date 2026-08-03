<!-- VIBETAGS-START -->
# Rules for ExceptionFlowParser

## Context & Focus
- **Focus**: Two-pass per class: (1) build call graph + collect TryStmt catch-boundaries → Group objects; (2) walk throws declarations → emit EXCEPTION_PROPAGATION edges. Exception nodes use 'exception:TypeName' id prefix for renderer detection.
- **Avoid**: Merging both passes into one — Pass 2 needs the complete caller map from Pass 1 to resolve propagation targets correctly.

## Architectural Boundary Constraints
- **Layer**: input
- **Prohibited References**: layout, render, cli
<!-- VIBETAGS-END -->
