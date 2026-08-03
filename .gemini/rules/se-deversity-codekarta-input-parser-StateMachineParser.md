<!-- VIBETAGS-START -->
# Rules for StateMachineParser

## Context & Focus
- **Focus**: Extracts enum-backed state machines. Enum constants become STATE nodes. Switch cases over state values and explicit transition(from,to[,event]) calls become TRANSITION edges.
- **Avoid**: Treating arbitrary enum usage as a state machine without a transition source/target. Parser must fail softly and return partial graphs.

## Architectural Boundary Constraints
- **Layer**: input
- **Prohibited References**: layout, render, cli
<!-- VIBETAGS-END -->
