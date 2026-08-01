---
paths: ["**/NodeType.java"]
---

<!-- VIBETAGS-START -->
# Rules for NodeType

## Locked Status
- **Reason**: String values are matched by identity in SvgRenderer's CSS class-name map and in all four parsers. Renaming or adding a constant requires updating SvgRenderer, all parser switch/if chains, and integration tests simultaneously.
<!-- VIBETAGS-END -->
