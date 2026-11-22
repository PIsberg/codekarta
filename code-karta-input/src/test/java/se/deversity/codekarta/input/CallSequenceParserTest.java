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

        // add() has no scope but is declared locally, so it is qualified with the
        // owning class and joins Calc's lifeline instead of a phantom "add" lane
        assertTrue(hasCallEdge(graph, "Calc.compute", "Calc.add"),
                "compute() must have a CALLS edge to Calc.add");
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


    // --- nested types, anonymous classes and overloads (#265) ---

    /**
     * {@code findAll(MethodDeclaration.class)} on a type reaches into its nested and anonymous
     * classes, so Inner's method was drawn a second time on Outer's lifeline and the anonymous
     * Runnable's run() became a method Outer never declared.
     */
    @Test
    void methodsOfNestedAndAnonymousClassesAreNotAttributedToTheEnclosingClass(@TempDir Path dir) throws Exception {
        Graph graph = parser.parse(write(dir, "Outer.java", """
                public class Outer {
                    void start() {
                        Runnable r = new Runnable() {
                            public void run() { helper(); }
                        };
                        r.run();
                    }
                    void helper() {}
                    static class Inner {
                        void innerMethod() {}
                    }
                }
                """));

        assertNotNull(graph.findNode("Inner.innerMethod"), "a nested class keeps its own methods");
        assertNull(graph.findNode("Outer.innerMethod"), "Outer does not declare innerMethod");
        assertNull(graph.findNode("Outer.run"), "Outer does not declare the anonymous Runnable's run");
        assertTrue(hasCallEdge(graph, "Outer.start", "Outer.helper"),
                "a call inside an anonymous class belongs to the method that declares it");
    }

    /**
     * Every overload shared the id {@code K.run}, so delegation from one overload to another
     * drew as a self-call and the overloads' call sequences were merged into one.
     */
    @Test
    void overloadedMethodsAreSeparateNodesAndDelegationIsNotASelfCall(@TempDir Path dir) throws Exception {
        Graph graph = parser.parse(write(dir, "K.java", """
                public class K {
                    void run(String a) { run(a, 1); }
                    void run(String a, int b) { work(); }
                    void work() {}
                }
                """));

        assertNotNull(graph.findNode("K.run(String)"));
        assertNotNull(graph.findNode("K.run(String,int)"));
        assertEquals("run", graph.findNode("K.run(String)").getLabel(), "the label stays the plain name");
        assertNull(graph.findNode("K.run"), "no merged node for both overloads");
        assertNotNull(graph.findNode("K.work"), "a method that is not overloaded keeps the plain id");
        assertTrue(hasCallEdge(graph, "K.run(String)", "K.run(String,int)"), "delegation targets the other overload");
        assertTrue(hasCallEdge(graph, "K.run(String,int)", "K.work"));
        assertTrue(graph.getEdges().stream().noneMatch(e -> e.getSourceId().equals(e.getTargetId())),
                "overload delegation is not a self-call");
    }

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

    @Test
    void methodsOfARecordAreSequenced(@TempDir Path dir) throws Exception {
        Path file = write(dir, "Order.java", """
                public record Order(int amount) {
                    public int price() { return helper(); }
                    int helper() { return amount; }
                }
                """);

        Graph graph = parser.parse(file);

        assertNotNull(graph.findNode("Order"), "a record is a participant");
        assertNotNull(graph.findNode("Order.price"));
        assertTrue(graph.getEdges().stream().anyMatch(e ->
                "Order.price".equals(e.getSourceId()) && "Order.helper".equals(e.getTargetId())));
    }

    @Test
    void methodsOfAnEnumAreSequenced(@TempDir Path dir) throws Exception {
        Path file = write(dir, "Status.java", """
                public enum Status {
                    NEW, PAID;
                    public Status next() { return advance(); }
                    Status advance() { return PAID; }
                }
                """);

        Graph graph = parser.parse(file);

        assertTrue(graph.getEdges().stream().anyMatch(e ->
                "Status.next".equals(e.getSourceId()) && "Status.advance".equals(e.getTargetId())));
    }

    @Test
    void anInterfaceIsTypedInterface(@TempDir Path dir) throws Exception {
        Path file = write(dir, "Api.java", """
                public interface Api {
                    default int twice(int x) { return helper(x) * 2; }
                    int helper(int x);
                }
                """);

        Graph graph = parser.parse(file);

        assertEquals("INTERFACE", graph.findNode("Api").getType());
    }
}
