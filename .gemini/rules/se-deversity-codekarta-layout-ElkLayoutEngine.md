<!-- VIBETAGS-START -->
# Rules for ElkLayoutEngine

## Context & Focus
- **Focus**: Group members must be laid out as children of compound ElkNodes; absolute coordinates are compound.x + child.x. ELK's SPI entries must be merged in the fat JAR.
- **Avoid**: Adding ELK options that are unsupported by the layered algorithm — any unknown property silently breaks layout and triggers the SimpleLayoutEngine fallback.

### Rules for method layout
- **Rule**: Optimal complexity required. O(n^2) is forbidden on hot paths.
- **Constraint**: Layout runs synchronously in the CLI pipeline — avoid O(n²) or heap-allocating operations on the full node list. ELK's layered algorithm is already O(n log n); the fallback SimpleLayoutEngine is O(n).

## Architectural Boundary Constraints
- **Layer**: layout
- **Prohibited References**: input, render, cli

## Strict Classpath Integrity
- **Rule**: Prohibit dynamic class loading, custom classloaders, runtime reflection hacks, or execution of dynamic external code.

### Rules for method layoutWithElk
- **Complexity Level**: MEDIUM
- **Rule**: Any logic modification requires updating a walkthrough/markdown file with structured architectural rationale.
<!-- VIBETAGS-END -->
