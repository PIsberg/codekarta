<!-- VIBETAGS-START -->
# Rules for JavaSourceInputParser

## Context & Focus
- **Focus**: Dispatch logic is path-type-based (isDirectory, filename == 'module-info.java', .java extension). All parsers are lazily initialised. The sequenceOnly flag selects between CallSequenceParser (no exception edges) and ExceptionFlowParser (call graph + try/catch Groups).
- **Avoid**: Adding new dispatch conditions without updating the InputParser contract javadoc and KartaCliTest expected output filenames.

## Architectural Boundary Constraints
- **Layer**: input
- **Prohibited References**: layout, render, cli
<!-- VIBETAGS-END -->
