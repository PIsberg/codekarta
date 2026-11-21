<!-- VIBETAGS-START -->
# Rules for FilterMatcher

## Mathematical Purity
- **Rule**: Must remain a pure function. Forbid state modifications and side effects.

### Rules for method matches
- **Reason**: Deterministic wildcard matching with no side effects

### Rules for method matchesAny
- **Reason**: Called once per node/edge candidate during parsing — deterministic string matching with no side effects; callers assume referential transparency
<!-- VIBETAGS-END -->
