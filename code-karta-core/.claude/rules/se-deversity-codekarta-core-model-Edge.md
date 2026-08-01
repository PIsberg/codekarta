---
paths: ["**/Edge.java"]
---

<!-- VIBETAGS-START -->
# Rules for Edge

## Core Functionality
- **Sensitivity**: High
- **Note**: IR directed edge. id/sourceId/targetId/type are required; label is optional (sequence number for CALLS, field name for HAS).

## Strict Type Safety
- **Rule**: Loose typing (e.g., Object, raw types, generic Map<String, Object>) is strictly prohibited. Enforce type safety.

## Schema & Serialization Safety
- **Rule**: Prohibit altering data formats, fields, database columns, or serialization structures without explicit backward-compatible migration paths.

## Domain Model Boundary
- **Purity**: Framework-free DDD Entity.
- **Allowed Imports**: com.fasterxml.jackson.annotation.JsonInclude, org.jspecify.annotations.Nullable
<!-- VIBETAGS-END -->
