---
paths: ["**/CallSequenceParser.java"]
---

<!-- VIBETAGS-START -->
# Rules for CallSequenceParser

## Context & Focus
- **Focus**: Produces integer-labelled CALLS edges in textual call order within each method. Node IDs use 'ClassName.methodName' qualified form. These integer labels are the contract read by SequenceDiagramRenderer to order messages.
- **Avoid**: Changing the CALLS edge label format — SequenceDiagramRenderer.isInteractionGraph() and orderMessages() depend on labels parsing as integers.

## Architectural Boundary Constraints
- **Layer**: input
- **Prohibited References**: layout, render, cli
<!-- VIBETAGS-END -->
