---
paths: ["**/Graph.java"]
---

<!-- VIBETAGS-START -->
# Rules for Graph

## Core Functionality
- **Sensitivity**: Critical
- **Note**: Central IR that flows between all three pipeline tiers. Contains only structural data — no business logic, no tier-specific knowledge. Layout coordinates (x/y/width/height) are the only mutable state, written exclusively by Tier 2 engines.

## Performance Constraints
- **Rule**: Optimal complexity required. O(n^2) is forbidden on hot paths.
- **Constraint**: findNode and addNodeIfAbsent must stay O(1). Both are called once per class, method and call site by every parser and both layout engines, so a linear scan makes graph construction O(n²) — measured at 20k nodes as 7.5 s of lookups against 4 ms indexed. Do not reintroduce a nodes.stream() search, and do not replace the id index with a scan 'for simplicity'.

## Architectural Boundary Constraints
- **Layer**: core
- **Prohibited References**: input, layout, render, cli

## Schema & Serialization Safety
- **Rule**: Prohibit altering data formats, fields, database columns, or serialization structures without explicit backward-compatible migration paths.

## Domain Model Boundary
- **Purity**: Framework-free DDD Entity.
- **Allowed Imports**: com.fasterxml.jackson.annotation.JsonIgnore, com.fasterxml.jackson.annotation.JsonInclude, org.jspecify.annotations.Nullable
<!-- VIBETAGS-END -->
