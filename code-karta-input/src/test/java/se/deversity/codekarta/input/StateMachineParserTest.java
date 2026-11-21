package se.deversity.codekarta.input;

import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.input.parser.StateMachineParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class StateMachineParserTest {

    private final StateMachineParser parser = new StateMachineParser();

    @Test
    void parsesEnumStatesAndSwitchTransitions(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("OrderWorkflow.java");
        Files.writeString(source, """
                public class OrderWorkflow {
                    enum State { NEW, PAID, SHIPPED, CANCELLED }
                    private State state = State.NEW;

                    void advance() {
                        switch (state) {
                            case NEW -> state = State.PAID;
                            case PAID -> state = State.SHIPPED;
                            case SHIPPED -> state = State.CANCELLED;
                            default -> {}
                        }
                    }
                }
                """);

        Graph graph = parser.parse(source);

        assertNotNull(graph.findNode("NEW"), "enum constants must become STATE nodes");
        assertNotNull(graph.findNode("PAID"), "enum constants must become STATE nodes");
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                "TRANSITION".equals(e.getType())
                        && "NEW".equals(e.getSourceId())
                        && "PAID".equals(e.getTargetId())),
                "switch case assignment must become a transition");
        assertTrue(graph.getEdges().stream().allMatch(e -> e.getLabel() != null),
                "transitions should carry a source method/event label");
        assertEquals(3, graph.getEdges().stream()
                        .filter(e -> "TRANSITION".equals(e.getType()))
                        .count(),
                "switch assignments should not be duplicated by the linear assignment fallback");
    }

    @Test
    void parsesExplicitTransitionDslCalls(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("TicketWorkflow.java");
        Files.writeString(source, """
                public class TicketWorkflow {
                    enum State { OPEN, REVIEW, CLOSED }

                    void configure() {
                        transition(State.OPEN, State.REVIEW, "submit");
                        transition("REVIEW", "CLOSED", "approve");
                    }
                }
                """);

        Graph graph = parser.parse(source);

        assertTransition(graph, "OPEN", "REVIEW", "submit");
        assertTransition(graph, "REVIEW", "CLOSED", "approve");
    }

    @Test
    void parsesDirectoryInputs(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("A.java"), """
                public class A {
                    enum State { IDLE, RUNNING }
                    void configure() { transition(State.IDLE, State.RUNNING, "start"); }
                }
                """);

        Graph graph = parser.parse(dir);

        assertTransition(graph, "IDLE", "RUNNING", "start");
    }

    @Test
    void parsesLocalVariableInitializerAsInitialState(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("Pipeline.java");
        Files.writeString(source, """
                public class Pipeline {
                    enum Stage { PARSING, LAYOUT, DONE }

                    void run() {
                        Stage stage = Stage.PARSING;   // local variable — not a field
                        stage = Stage.LAYOUT;
                        stage = Stage.DONE;
                    }
                }
                """);

        Graph graph = parser.parse(source);

        assertTrue(graph.getEdges().stream().anyMatch(e ->
                "TRANSITION".equals(e.getType())
                        && "PARSING".equals(e.getSourceId())
                        && "LAYOUT".equals(e.getTargetId())),
                "PARSING→LAYOUT must be detected when initial state comes from a local variable declaration");
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                "TRANSITION".equals(e.getType())
                        && "LAYOUT".equals(e.getSourceId())
                        && "DONE".equals(e.getTargetId())),
                "LAYOUT→DONE must follow from subsequent assignments");
        assertEquals(2, graph.getEdges().stream()
                .filter(e -> "TRANSITION".equals(e.getType())).count(),
                "exactly 2 transitions from a 3-state linear sequence");
    }

    private void assertTransition(Graph graph, String source, String target, String label) {
        Edge edge = graph.getEdges().stream()
                .filter(e -> "TRANSITION".equals(e.getType()))
                .filter(e -> source.equals(e.getSourceId()))
                .filter(e -> target.equals(e.getTargetId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("missing transition " + source + " -> " + target));
        assertEquals(label, edge.getLabel());
    }

    @Test
    void repositoryRootSkipsStateMachinesDeclaredInTests(@TempDir Path repo) throws Exception {
        Files.writeString(repo.resolve("pom.xml"), "<project/>");
        Path testDir = Files.createDirectories(repo.resolve("src/test/java"));
        Files.writeString(testDir.resolve("FakeWorkflow.java"), """
                public class FakeWorkflow {
                    enum Phase { START, END }
                    private Phase phase = Phase.START;
                    void go() { switch (phase) { case START -> phase = Phase.END; default -> {} } }
                }
                """);

        assertTrue(parser.parse(repo).getNodes().isEmpty(), "test sources must not be read by default");
        assertNotNull(new StateMachineParser(true).parse(repo).findNode("START"),
                "includeTests must read test sources");
    }

    // --- state variables by type, enums from other files (#228) ---

    private static final String ORDER_WITH_STATUS_FIELD = """
            public class Order {
                private OrderStatus status = OrderStatus.NEW;
                void pay()  { switch (status) { case NEW -> status = OrderStatus.PAID; default -> {} } }
                void ship() { this.status = OrderStatus.SHIPPED; }
            }
            """;

    @Test
    void aFieldTypedAsTheEnumIsAStateVariableWhateverItIsCalled(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("Order.java");
        Files.writeString(source, ORDER_WITH_STATUS_FIELD.replace(
                "public class Order {", "public class Order {\n    enum OrderStatus { NEW, PAID, SHIPPED }"));

        Graph graph = parser.parse(source);

        assertTransition(graph, "NEW", "PAID", "pay");
        assertTransition(graph, "NEW", "SHIPPED", "ship");
    }

    @Test
    void anEnumDeclaredInAnotherFileDrivesTheTransitionsOfADirectory(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("OrderStatus.java"), "public enum OrderStatus { NEW, PAID, SHIPPED }");
        Files.writeString(dir.resolve("Order.java"), ORDER_WITH_STATUS_FIELD);

        Graph graph = parser.parse(dir);

        assertTransition(graph, "NEW", "PAID", "pay");
        assertTransition(graph, "NEW", "SHIPPED", "ship");
    }

    @Test
    void aLocalTypedAsTheEnumIsAStateVariable(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Phase.java"), "public enum Phase { PLAN, BUILD, SHIP }");
        Files.writeString(dir.resolve("Release.java"), """
                public class Release {
                    void run() {
                        Phase current = Phase.PLAN;
                        current = Phase.BUILD;
                        current = Phase.SHIP;
                    }
                }
                """);

        Graph graph = parser.parse(dir);

        assertTransition(graph, "PLAN", "BUILD", "run");
        assertTransition(graph, "BUILD", "SHIP", "run");
    }

    @Test
    void aSwitchThatMapsOneEnumOntoAnotherIsNotATransition(@TempDir Path dir) throws Exception {
        Path source = dir.resolve("Mapper.java");
        Files.writeString(source, """
                public class Mapper {
                    enum OrderStatus { NEW, PAID }
                    enum PaymentStatus { PENDING, CAPTURED }
                    PaymentStatus toPayment(OrderStatus s) {
                        switch (s) {
                            case NEW: return PaymentStatus.PENDING;
                            case PAID: return PaymentStatus.CAPTURED;
                            default: return PaymentStatus.PENDING;
                        }
                    }
                }
                """);

        Graph graph = parser.parse(source);

        assertEquals(0, graph.getEdges().size(),
                "NEW and PENDING belong to different enums, so mapping one to the other is not a state change");
    }
}
