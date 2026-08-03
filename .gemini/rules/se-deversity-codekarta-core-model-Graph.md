<!-- VIBETAGS-START -->
# Rules for Graph

## Core Functionality
- **Sensitivity**: Critical
- **Note**: Central IR that flows between all three pipeline tiers. Contains only structural data — no business logic, no tier-specific knowledge. Layout coordinates (x/y/width/height) are the only mutable state, written exclusively by Tier 2 engines.

## Architectural Boundary Constraints
- **Layer**: core
- **Prohibited References**: input, layout, render, cli

## Schema & Serialization Safety
- **Rule**: Prohibit altering data formats, fields, database columns, or serialization structures without explicit backward-compatible migration paths.

## Domain Model Boundary
- **Purity**: Framework-free DDD Entity.
- **Allowed Imports**: com.fasterxml.jackson.annotation.JsonInclude, org.jspecify.annotations.Nullable
<!-- VIBETAGS-END -->
