<!-- VIBETAGS-START -->
# Rules for Group

## Core Functionality
- **Sensitivity**: Medium
- **Note**: IR cluster — maps a label (package, module boundary, try/catch region) to a set of node IDs. Used by the render tier to draw bounding frames.

## Strict Type Safety
- **Rule**: Loose typing (e.g., Object, raw types, generic Map<String, Object>) is strictly prohibited. Enforce type safety.

## Schema & Serialization Safety
- **Rule**: Prohibit altering data formats, fields, database columns, or serialization structures without explicit backward-compatible migration paths.

## Domain Model Boundary
- **Purity**: Framework-free DDD Entity.
- **Allowed Imports**: com.fasterxml.jackson.annotation.JsonInclude, org.jspecify.annotations.Nullable
<!-- VIBETAGS-END -->
