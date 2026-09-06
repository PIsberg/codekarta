---
paths: ["**/JsonRenderer.java"]
---

<!-- VIBETAGS-START -->
# Rules for JsonRenderer

## Architectural Boundary Constraints
- **Layer**: render
- **Prohibited References**: input, layout, cli

## Idempotency Guarantee
- **Rule**: This operation is idempotent. Calling it multiple times must produce the same result as calling it once.
- **Reason**: Two renders of the same graph must be byte-identical: map keys are sorted and the pretty printer is pinned to , because HashMap iteration order and System.lineSeparator() are both unstable. KartaCli's byte-identical-output invariant, and the CI step that diffs regenerated files, depend on it.
<!-- VIBETAGS-END -->
