<!-- VIBETAGS-START -->
# Rules for LayoutEngine

## Contract-Frozen Signature
- **Constraint**: You may change internal logic, but MUST NOT modify the method name, parameters, return type, or checked exceptions.
- **Reason**: layout(Graph) must return the same Graph instance (mutated in-place) so callers can chain. Implementations must set x, y, width, height on every Node. Nodes with unresolvable positions must be left at null — SvgRenderer silently skips them.

## Architectural Boundary Constraints
- **Layer**: layout
- **Prohibited References**: input, render, cli

## Polymorphic Extension Pattern
- **Pattern**: STRATEGY_PATTERN
- **Rule**: Open for extension, closed for modification. Use strategy or visitor subclasses instead of changing this file.
<!-- VIBETAGS-END -->
