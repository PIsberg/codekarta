---
paths: ["**/SourceTree.java"]
---

<!-- VIBETAGS-START -->
# Rules for SourceTree

## Context & Focus
- **Focus**: The one definition of which files under a directory count as source. Parsers call javaFiles() instead of Files.walk so that a repository root, a module, and a source root all behave the same for every diagram type.
- **Avoid**: Pruning the root itself, or pruning by name alone without the build-file check: 'build' and 'target' are legal package names, and a user who points --input at src/test/java asked for tests.

## Architectural Boundary Constraints
- **Layer**: input
- **Prohibited References**: layout, render, cli
<!-- VIBETAGS-END -->
