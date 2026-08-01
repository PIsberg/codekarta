---
paths: ["**/SvgRenderer.java"]
---

<!-- VIBETAGS-START -->
# Rules for SvgRenderer

## Context & Focus
- **Focus**: CSS class names node-rect, edge-line, group-rect, node-label, edge-label are stable contract points — consumers inject custom themes via the cssString param. Interaction graphs (METHOD nodes + integer CALLS labels) are automatically routed to SequenceDiagramRenderer.
- **Avoid**: Renaming or removing any CSS class — breaks existing stylesheets. Hardcoding a diagram-type field on Graph — detection must remain content-based.

## Strict Test Isolation
- **Rule**: Strict test isolation required. AI-generated or modified tests must not share mutable state, rely on execution order, or conflict on external resources.

## Architectural Boundary Constraints
- **Layer**: render
- **Prohibited References**: input, layout, cli

## Public API Surface Protection
- **Rule**: Exposes public API. Preserve signature, Javadoc, and behavior without breaking backwards or source compatibility.

### Rules for method renderNode
- **Rule**: This looks removable but is deliberate. Refactor only while the invariant holds.
- **Invariant**: A node with a null x or y renders as the empty string rather than raising or substituting a default position.
- **Breaks if changed**: Throwing here turns a partially-laid-out graph into no diagram at all, and defaulting the coordinate to 0 stacks every unpositioned node on the origin — both are worse than the node being absent. The LayoutEngine contract deliberately leaves unresolvable positions null and names this skip as the counterpart.
- **Audit**: Not a defect — do not flag.
<!-- VIBETAGS-END -->
