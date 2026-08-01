---
paths: ["**/NodeDimensions.java"]
---

<!-- VIBETAGS-START -->
# Rules for NodeDimensions

## Locked Status
- **Reason**: Shared sizing constants consumed by all three tiers — SimpleLayoutEngine, ElkLayoutEngine, and SvgRenderer. Changing these values shifts node dimensions globally and will break layout tests.

## Immutable Type
- **Rule**: This type is immutable. Never introduce non-final fields, setters, or mutating methods.
- **Note**: Constants-only holder. No instance state may ever be added — this class is used across all three pipeline tiers.
<!-- VIBETAGS-END -->
