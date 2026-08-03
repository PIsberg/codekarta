<!-- VIBETAGS-START -->
# Rules for ModuleInfoParser

## Context & Focus
- **Focus**: Parses module-info.java directives only: 'requires' → REQUIRES edge between MODULE nodes; 'exports' → EXPORTS edge to a PACKAGE node. The module name itself becomes the root MODULE node.
- **Avoid**: Parsing class files or anything outside the module declaration — this parser is scoped to module-info.java exclusively.

## Architectural Boundary Constraints
- **Layer**: input
- **Prohibited References**: layout, render, cli
<!-- VIBETAGS-END -->
