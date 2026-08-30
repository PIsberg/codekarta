# Example Project

A complete fixture lives in [`example-shipping-system/`](../example-shipping-system/). It is the
canonical integration-test target: the integration tests under
`code-karta-input/src/test/java/se/deversity/codekarta/input/integration/` verify every diagram
type against it, so the fixture is not decoration — changing it changes what the tests assert.

Pre-generated diagrams are in [`example-shipping-system/diagrams/`](../example-shipping-system/diagrams/).

| File | Source | Shows |
|---|---|---|
| [`module-diagram.svg`](../example-shipping-system/diagrams/module-diagram.svg) | `module-info.java` | JPMS `requires` and `exports` from the shipping module descriptor |
| [`class-diagram.svg`](../example-shipping-system/diagrams/class-diagram.svg) | `shipping/domain/` | `ShippingUnit`, `Cargo`, and `ExpressCargo` relationships |
| [`orderprocessor-sequence-diagram.svg`](../example-shipping-system/diagrams/orderprocessor-sequence-diagram.svg) | `OrderProcessor.java` | `OrderProcessor` method calls and exception flow |
| [`shipmentlifecycle-state-machine-diagram.svg`](../example-shipping-system/diagrams/shipmentlifecycle-state-machine-diagram.svg) | `state/ShipmentLifecycle.java` | Switch-case transitions: `CREATED → PROCESSING → IN_TRANSIT → DELIVERED / FAILED / CANCELLED` |
| [`paymentworkflow-state-machine-diagram.svg`](../example-shipping-system/diagrams/paymentworkflow-state-machine-diagram.svg) | `state/PaymentWorkflow.java` | Explicit `transition(from, to, event)` DSL: payment states from `PENDING` through `CAPTURED` or `DECLINED` |
| [`inventoryreservation-state-machine-diagram.svg`](../example-shipping-system/diagrams/inventoryreservation-state-machine-diagram.svg) | `state/InventoryReservation.java` | Linear state assignments: `IDLE → CHECKING → RESERVED → ALLOCATED → COMMITTED` |

The three state-machine files each exercise a different detection pattern in `StateMachineParser`:
switch-case, explicit transition DSL, and linear assignment. Between them they cover the parser's
whole detection surface — see [`DIAGRAM-MODES.md`](DIAGRAM-MODES.md#state-transition-diagram).

## Regenerating the fixture diagrams

```bash
mvn clean package -q

JAR=code-karta-cli/target/code-karta-cli-0.4.0-all.jar
STATE=example-shipping-system/src/main/java/com/karta/shipping/state

# Module, class, and sequence diagrams
java -jar $JAR --input example-shipping-system/src/main/java/module-info.java --output example-shipping-system/diagrams
java -jar $JAR --input example-shipping-system/src/main/java/com/karta/shipping/domain --output example-shipping-system/diagrams
java -jar $JAR --input example-shipping-system/src/main/java/com/karta/shipping/core/OrderProcessor.java --output example-shipping-system/diagrams

# State machine diagrams (one per pattern)
java -jar $JAR --input $STATE/ShipmentLifecycle.java    --state-machine --output example-shipping-system/diagrams
java -jar $JAR --input $STATE/PaymentWorkflow.java      --state-machine --output example-shipping-system/diagrams
java -jar $JAR --input $STATE/InventoryReservation.java --state-machine --output example-shipping-system/diagrams
```

## Building the example itself

The fixture is a real project with its own dual build:

```bash
cd example-shipping-system
mvn compile
./gradlew compileJava
```

Note the package: the fixture uses `com.karta.shipping.*`, while code-karta's own modules use
`se.deversity.codekarta.*`. Paths that look inconsistent between this page and the rest of the
docs are correct for that reason.
