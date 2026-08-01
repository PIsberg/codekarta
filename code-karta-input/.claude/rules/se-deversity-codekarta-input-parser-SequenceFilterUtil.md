---
paths: ["**/SequenceFilterUtil.java"]
---

<!-- VIBETAGS-START -->
# Rules for SequenceFilterUtil

## Context & Focus
- **Focus**: SKIP_METHODS is a scoped-call filter only — it applies to receiver.method() calls. Unscoped calls (direct local method invocations) are never filtered because they represent intra-class domain logic. shouldSkipScopedCall() is the single decision point used by all parsers.
- **Avoid**: Adding method names that could be legitimate domain operations (e.g. 'process', 'execute', 'run') — only add names that are unambiguously stdlib/infrastructure noise regardless of context.
<!-- VIBETAGS-END -->
