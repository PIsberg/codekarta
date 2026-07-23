```markdown

\# Specification: code-karta (Java Architecture Mapping Engine)



<system\_context>

\- \*\*Language:\*\* Java (JDK 24+)

\- \*\*Build System:\*\* Maven and Gradle Multi-Module Project

\- \*\*Core Philosophy:\*\* Strictly decoupled, compiler-style 3-tier mapping pipeline. Zero visual logic in the parser; zero language logic in the renderer.

</system\_context>



\---



\## 1. Multi-Module Project Architecture



The project must be strictly divided into independent sub-modules to enforce decoupling.





```



code-karta/

├── pom.xml (Root Reactor)

├── code-karta-input/          # Tier 1: AST Extraction \& IR Serialization

├── code-karta-core/           # Agnostic Internal Representation (IR) Data Models

├── code-karta-layout/         # Tier 2: Spatial Layout Calculation (ELK/Graphviz Adapters)

└── code-karta-render/         # Tier 3: Vector Graph Generation (SVG/Canvas)



```



<architecture\_blueprint>



\### 📦 `code-karta-core`

Contains the immutable, language-agnostic intermediate JSON schema.

\- \*\*Node:\*\* `{ id: String, type: String, label: String, properties: Map }`

\- \*\*Edge:\*\* `{ id: String, sourceId: String, targetId: String, type: String, label: String }`

\- \*\*Group:\*\* Bounded clusters/subgraphs representing namespaces, modules, or boundaries.



\### 📦 `code-karta-input`

Parses codebase targets into the intermediate representation. Focuses entirely on structural semantics using reflection, annotation processing, or Tree-sitter.



\### 📦 `code-karta-layout`

Consumes the flat Core IR graph, pipes it through a layout engine provider (e.g., Eclipse Layout Kernel via Java bindings), calculates absolute coordinate bounding boxes `(x, y, width, height)` for every item, and appends them to the IR payload.



\### 📦 `code-karta-render`

Takes the spatially computed IR graph and maps it directly to a vector or pixel canvas (defaulting to pure, clean, style-injectable SVGs).

</architecture\_blueprint>



\---



\## 2. Ingestion Strategies \& Technical Boundaries



<ingestion\_rules>

\- \*\*AST Extraction:\*\* Must run zero-config on standard compiled Java classes or source trees.

\- \*\*Annotation Support:\*\* Native discovery of custom markers like `@ArchitectureComponent` or standard Spring/Jakarta annotations to group system components automatically.

</ingestion\_rules>



<dependency\_stack>

\- \*\*Tier 1 (Input/AST):\*\* `com.github.javaparser:javaparser-core`

\- \*\*Tier 2 (Layout Math):\*\* `guru.nidi:graphviz-java` or Eclipse Layout Kernel (ELK)

\- \*\*Tier 3 (Rendering):\*\* No visual libraries. Pure Java String/XML compilation to SVG.

</dependency\_stack>



\---



\## 3. AI-Native Code Design Guardrails



When generating code or making modifications within this repository, the following design parameters are \*\*non-negotiable\*\*:



<ai\_guardrails>

1\. \*\*High Fault Tolerance:\*\* Input parsers must never crash on malformed input or syntax omissions. If a structural edge cannot be verified, log a warning, bypass it, and infer the component hierarchy gracefully.

2\. \*\*Token Efficiency:\*\* The JSON schema serialized between `input` -> `layout` -> `render` must remain highly compressed. Avoid verbose object schemas. Prefer flat node/edge maps to save LLM context tokens when parsing large codebases.

3\. \*\*Strict Semantic Separation:\*\* Do not let layout spatial logic leak into the input or parser layers. The AI agent or parser states \*what\* connects to \*what\*; the layout sub-module calculates \*where\* it sits.

</ai\_guardrails>



\---



\## 4. Verification Fixture: Example Target Project



To validate the multi-module parser pipeline, use this target blueprint as your canonical integration test fixture. The parser must successfully extract and visualize three distinct diagram types from this single target.



\### Target Project Structural Tree



```



example-shipping-system/

├── module-info.java (Target 1: Module Diagram verification)

├── src/

│   └── com/karta/shipping/

│       ├── core/

│       │   ├── OrderProcessor.java (Target 3: Sequence Diagram verification)

│       │   └── InventoryService.java

│       └── domain/ (Target 2: Class Diagram verification)

│           ├── ShippingUnit.java (Interface)

│           ├── Cargo.java (Implements ShippingUnit)

│           └── ExpressCargo.java (Extends Cargo)



```



\### Expected Map Extraction Behaviors



\#### A. Module Diagrams

\- \*\*Source:\*\* Scans `module-info.java`.

\- \*\*Expected Graph:\*\* Extract a cluster map showing module boundaries, exported packages, and cross-module dependencies (`requires` clauses).



\#### B. Class Diagrams

\- \*\*Source:\*\* Scans the `domain/` directory.

\- \*\*Expected Graph:\*\* 

&#x20; - \*\*Inheritance:\*\* `ExpressCargo` node shows a structural inheritance link (`extends`) pointing to `Cargo`.

&#x20; - \*\*Interface Compliance:\*\* `Cargo` node shows an implementation link (`implements`) pointing to the `ShippingUnit` interface.

&#x20; - \*\*Relations:\*\* Associations/fields inside classes mapped as clear directed edges.



\#### C. Call/Function Sequences

\- \*\*Source:\*\* Scans code blocks inside `OrderProcessor.java`.

\- \*\*Expected Graph:\*\* Traces ordered method invocations (e.g., `OrderProcessor.submit()` calling `InventoryService.checkStock()`) to build an ordered timeline sequence model in the IR layer.


#### D. Exception Flow Mapping (The Shattered Path)
- **Source Input:** Iterates through method execution code blocks containing try-catch blocks and explicit throw statements.
- **Expected Core Graph Properties:**
  - **Edge Type Differentiation:** Differentiates between normal execution paths (`type: "CALL"`) and exception propagation lines (`type: "EXCEPTION_PROPAGATION"`).
  - **Catch Boundaries:** Any `TryStmt` found must encapsulate its inner method call nodes within a distinct `GraphGroup`.
  - **Uncaught Propagation:** If a method throws or propagates a checked exception listed in its signature, generate a directed exception edge passing completely out of that method node up to its verified caller node.

##### Integration Test Reference Fixture
```java
package se.deversity.codekarta.shipping.core;

public class OrderProcessor {
    private final InventoryService inventory;

    public void processOrder(String orderId) {
        try {
            inventory.checkStock(orderId);
        } catch (InventoryException e) {
            // This is an Exception Catch Boundary Node
            System.out.println("Handling inventory failure safely.");
        }
    }

    public void submit() throws OrderValidationException {
        boolean valid = false;
        if (!valid) {
            throw new OrderValidationException("Invalid order structural data");
        }
    }
}


```



```

---

## 5. CLI Module & Output

### 📦 `code-karta-cli`

Thin driver that wires all three tiers together and writes SVG files to an output directory.

**Command syntax:**

```
java -jar code-karta-cli-all.jar --input <path> [--output <dir>]
```

| Argument | Default | Description |
|---|---|---|
| `--input <path>` | required | `module-info.java` → module diagram; directory → class diagram; `*.java` file → sequence diagram |
| `--output <dir>` | `./output` | Directory where SVG files are written (created if absent) |

**Output filename convention:**

| Input | Output filename |
|---|---|
| `module-info.java` | `module-diagram.svg` |
| directory | `class-diagram.svg` |
| `Foo.java` | `foo-sequence-diagram.svg` |

**Output location:** SVG files are written to the `--output` directory (default `./output/`), created automatically if absent. Open any `.svg` in a browser or IDE SVG viewer.

Pre-generated example diagrams are committed at `example-shipping-system/diagrams/`:
- `module-diagram.svg` — module boundary map
- `class-diagram.svg` — class/interface hierarchy
- `orderprocessor-sequence-diagram.svg` — method call sequence

**Packaging:** Both build systems produce a self-contained fat JAR.
- Maven: `mvn clean package` → `code-karta-cli/target/code-karta-cli-1.0-SNAPSHOT-all.jar`
- Gradle: `./gradlew :code-karta-cli:fatJar` → `code-karta-cli/build/libs/code-karta-cli-1.0-SNAPSHOT-all.jar`

**Running during development:**
- Maven: `mvn -pl code-karta-cli exec:java -Dexec.mainClass=se.deversity.codekarta.cli.KartaCli "-Dexec.args=--input src/domain --output target/diagrams"`
- Gradle: `./gradlew :code-karta-cli:run --args="--input src/domain --output build/diagrams"`

