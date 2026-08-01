---
paths: ["**/ParserSupport.java"]
---

<!-- VIBETAGS-START -->
# Rules for ParserSupport

## Context & Focus
- **Focus**: Only the mechanical parse skeleton belongs here — JavaParser configuration, exclude normalisation, and the per-class iteration every single-file parser repeats. Helpers stay caller-agnostic: they take the Graph and a callback rather than deciding what nodes or edges mean.
- **Avoid**: Absorbing diagram semantics from the callers. A helper that knows about CALLS labels, EXCEPTION_PROPAGATION edges, or try/catch Groups belongs in the parser that owns that diagram type, not in the shared skeleton.

## Architectural Boundary Constraints
- **Layer**: input
- **Prohibited References**: layout, render, cli

## Load-Bearing Oddity
- **Rule**: This looks removable but is deliberate. Refactor only while the invariant holds.
- **Invariant**: parseJava21 propagates its failures instead of swallowing them; the try/catch that turns a failure into a partial graph stays in each calling parser.
- **Breaks if changed**: Wrapping the fault tolerance in here looks like deduplication and silently changes the contract: every caller would inherit one shared recovery policy, and a parser that needs to log its own diagnostic or return a differently-shaped partial graph could no longer do so. The repeated try/catch in the callers is the fault-tolerance rule being stated once per parser, not copy-paste.
- **Audit**: Not a defect — do not flag.
<!-- VIBETAGS-END -->
