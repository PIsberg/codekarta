<!-- VIBETAGS-START -->
# Rules for SimpleLayoutEngine

## Context & Focus
- **Focus**: BFS from root nodes (no incoming edges) assigns depth levels → rows; siblings within a row become columns. Isolated nodes fall back to level 0. Cyclic graphs seed BFS from the first node. Row Y positions are computed dynamically from the tallest estimated node height in each row so that compartment-heavy class nodes never overlap the row below.
- **Avoid**: Changing NodeDimensions.DEFAULT_WIDTH/HEIGHT — those constants are @AILocked and consumed by both layout engines and SvgRenderer.

## Architectural Boundary Constraints
- **Layer**: layout
- **Prohibited References**: input, render, cli
<!-- VIBETAGS-END -->
