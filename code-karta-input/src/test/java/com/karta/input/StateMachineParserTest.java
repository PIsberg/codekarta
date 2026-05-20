package com.karta.input;

import com.karta.core.model.Edge;
import com.karta.core.model.Graph;
import com.karta.input.parser.StateMachineParser;
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
}
