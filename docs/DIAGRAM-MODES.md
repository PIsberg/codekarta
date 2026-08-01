# Diagram Modes

The input path decides the diagram type; flags refine it.

| Input | Mode | Output file |
|---|---|---|
| `module-info.java` | JPMS module diagram | `module-diagram.svg` |
| Directory | Class diagram | `class-diagram.svg` |
| Single `.java` file | Exception-flow sequence diagram | `<lowercase-classname>-sequence-diagram.svg` |
| Single `.java` file plus `--sequence-only` | Call-only sequence diagram | `<lowercase-classname>-sequence-diagram.svg` |
| Directory plus `--sequence-only` | Multi-file stitched sequence diagram | `sequence-diagram.svg` |
| File or directory plus `--state-machine` | State transition diagram | `<lowercase-classname>-state-machine-diagram.svg` or `state-machine-diagram.svg` |
| Directory plus `--modules-only` | Cross-module diagram | `modules-diagram.svg` |

For rendered examples of each, see [`DIAGRAM-GALLERY.md`](DIAGRAM-GALLERY.md).

## Module Diagram

Parses a JPMS module descriptor into module and package nodes with `REQUIRES` and `EXPORTS` edges.

```bash
java -jar code-karta-cli/target/code-karta-cli-0.2.0-all.jar \
  --input example-shipping-system/src/main/java/module-info.java \
  --output example-shipping-system/diagrams
```

With `--modules-only` on a directory, the parser falls back to the build reactor — Maven
`<modules>` or Gradle `include(...)` — when the project declares no JPMS modules. Reactor
membership comes from the build files, never the directory layout: a directory holding a
`pom.xml` is not a module unless some aggregator lists it.

## Class Diagram

Parses a directory of Java files into class and interface nodes with `EXTENDS`, `IMPLEMENTS`, and
`HAS` edges. Standard library types such as `String`, primitives, and common collections are
filtered out so diagrams focus on domain types.

Classes are **automatically grouped by Java package** into nested compound blocks. Filter out
specific classes, packages, or frameworks with `--exclude`.

```bash
java -jar code-karta-cli/target/code-karta-cli-0.2.0-all.jar \
  --input example-shipping-system/src/main/java/com/karta/shipping/domain \
  --output example-shipping-system/diagrams
```

## Exception-Flow Sequence Diagram

Parses one Java file into method nodes and ordered call edges. The default file mode also includes
exception propagation and try/catch regions when the parser can identify them.

```bash
java -jar code-karta-cli/target/code-karta-cli-0.2.0-all.jar \
  --input code-karta-cli/src/main/java/se/deversity/codekarta/cli/KartaCli.java \
  --output docs/diagrams
```

## Call-Only Sequence Diagram

Use `--sequence-only` when you want a cleaner call graph without exception-flow annotations.

```bash
java -jar code-karta-cli/target/code-karta-cli-0.2.0-all.jar \
  --input code-karta-input/src/main/java/se/deversity/codekarta/input/parser/CallSequenceParser.java \
  --output docs/diagrams \
  --sequence-only
```

## Multi-File Stitched Sequence Diagram

Directory input plus `--sequence-only` parses all `.java` files below the directory and uses
JavaParser symbol solving to stitch calls across files. The key difference from single-file mode:
a call like `inventoryService.checkStock()` resolves to `InventoryService.checkStock` and connects
to the node created when `InventoryService.java` is parsed, rather than dangling as an unowned node.

Point `--input` at a source root such as `src/main/java` when you need cross-package resolution.

```bash
java -jar code-karta-cli/target/code-karta-cli-0.2.0-all.jar \
  --input example-shipping-system/src/main/java/com/karta/shipping/core \
  --output docs/diagrams \
  --sequence-only
```

## State Transition Diagram

Use `--state-machine` for enum-backed workflow code. Enum constants become `STATE` nodes. The
parser recognizes switch cases that assign, return, or yield another enum constant, plus explicit
`transition(from, to, event)` calls.

```java
class Workflow {
  enum State { OPEN, REVIEW, CLOSED }

  void configure() {
    transition(State.OPEN, State.REVIEW, "submit");
    transition(State.REVIEW, State.CLOSED, "approve");
  }
}
```

```bash
java -jar code-karta-cli/target/code-karta-cli-0.2.0-all.jar \
  --input code-karta-cli/src/main/java/se/deversity/codekarta/cli/KartaCli.java \
  --state-machine --output docs/diagrams
```

The shipping fixture carries one file per detection pattern — see
[`EXAMPLE-PROJECT.md`](EXAMPLE-PROJECT.md).
