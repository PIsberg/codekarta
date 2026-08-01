---
paths: ["**/EdgeType.java"]
---

<!-- VIBETAGS-START -->
# Rules for EdgeType

## Locked Status
- **Reason**: String values are used as CSS class selectors in SvgRenderer and as edge-type identifiers emitted by all parsers. Adding, removing, or renaming a constant requires coordinated changes across the entire pipeline.
<!-- VIBETAGS-END -->
