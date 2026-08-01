---
paths: ["**/Node.java"]
---

<!-- VIBETAGS-START -->
# Rules for Node

## Core Functionality
- **Sensitivity**: High
- **Note**: IR vertex. Fields id/type/label/properties are the stable serialised schema; x/y/width/height are layout-only and may be null before Tier 2 runs.

## Strict Type Safety
- **Rule**: Loose typing (e.g., Object, raw types, generic Map<String, Object>) is strictly prohibited. Enforce type safety.

## Schema & Serialization Safety
- **Rule**: Prohibit altering data formats, fields, database columns, or serialization structures without explicit backward-compatible migration paths.

## Domain Model Boundary
- **Purity**: Framework-free DDD Entity.
- **Allowed Imports**: com.fasterxml.jackson.annotation.JsonInclude, org.jspecify.annotations.Nullable
<!-- VIBETAGS-END -->
