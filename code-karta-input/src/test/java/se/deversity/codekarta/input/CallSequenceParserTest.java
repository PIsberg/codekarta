package se.deversity.codekarta.input;

import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.input.parser.CallSequenceParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class CallSequenceParserTest {

    private final CallSequenceParser parser = new CallSequenceParser();

    @Test
    void parsesClassNode(@TempDir Path dir) throws Exception {
        Path file = write(dir, "Greeter.java", "public class Greeter {}");
        Graph graph = parser.parse(file);
        assertNotNull(graph.findNode("Greeter"));
        assertEquals("CLASS", graph.findNode("Greeter").getType());
    }

    @Test
    void parsesMethodNode(@TempDir Path dir) throws Exception {
        Path file = write(dir, "Greeter.java", """
                public class Greeter {
                    void hello() {}
                }
                """);
        Graph graph = parser.parse(file);
        assertNotNull(graph.findNode("Greeter.hello"));
        assertEquals("METHOD", graph.findNode("Greeter.hello").getType());
    }

    @Test
    void parsesCallEdge(@TempDir Path dir) throws Exception {
        Path file = write(dir, "Processor.java", """
                public class Processor {
                    private Service svc = new Service();
                    void process() {
                        svc.execute();
                    }
                }
                class Service {
                    void execute() {}
                }
                """);
        Graph graph = parser.parse(file);

        assertTrue(hasCallEdge(graph, "Processor.process", "svc.execute"),
                "process() must have a CALLS edge to svc.execute()");
    }

    @Test
    void callEdgeCarriesSequenceLabel(@TempDir Path dir) throws Exception {
        Path file = write(dir, "A.java", """
                public class A {
                    B b = new B();
                    void run() {
                        b.first();
                        b.second();
                    }
                }
                class B {
                    void first() {}
                    void second() {}
                }
                """);
        Graph graph = parser.parse(file);

        List<Edge> callEdges = graph.getEdges().stream()
                .filter(e -> "CALLS".equals(e.getType()) && "A.run".equals(e.getSourceId()))
                .sorted((a, b2) -> Integer.compare(
                        Integer.parseInt(a.getLabel()), Integer.parseInt(b2.getLabel())))
                .toList();

        assertEquals(2, callEdges.size());
        assertEquals("1", callEdges.get(0).getLabel());
        assertEquals("2", callEdges.get(1).getLabel());
    }

    @Test
    void parsesThisCallWithNoScope(@TempDir Path dir) throws Exception {
        Path file = write(dir, "Calc.java", """
                public class Calc {
                    int compute() {
                        return add(1, 2);
                    }
                    int add(int a, int b) { return a + b; }
                }
                """);
        Graph graph = parser.parse(file);

        // add() has no scope, so callee id is just "add"
        assertTrue(hasCallEdge(graph, "Calc.compute", "add"),
                "compute() must have a CALLS edge to add");
    }

    @Test
    void returnsEmptyGraphOnMalformedInput(@TempDir Path dir) throws Exception {
        Path file = write(dir, "Bad.java", "not java code %%%");
        Graph graph = parser.parse(file);
        assertNotNull(graph);
    }

    @Test
    void returnsEmptyGraphForNonExistentFile() {
        Graph graph = parser.parse(Path.of("no-such-file.java"));
        assertNotNull(graph);
        assertTrue(graph.getNodes().isEmpty());
    }

    // --- helpers ---

    private boolean hasCallEdge(Graph graph, String caller, String callee) {
        return graph.getEdges().stream().anyMatch(e ->
                "CALLS".equals(e.getType())
                        && caller.equals(e.getSourceId())
                        && callee.equals(e.getTargetId()));
    }

    private Path write(Path dir, String name, String content) throws Exception {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
