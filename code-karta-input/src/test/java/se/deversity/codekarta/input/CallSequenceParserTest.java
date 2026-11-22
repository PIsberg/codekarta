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

        assertTrue(hasCallEdge(graph, "Processor.process", "Service.execute"),
                "process() must have a CALLS edge to Service.execute(), typed by svc's declaration");
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


    /** forEachIncludedClass tested only the simple name, so a package pattern never matched (#266). */
    @Test
    void aPackageExcludeDropsTheClassesOfThatPackage(@TempDir Path dir) throws Exception {
        Path file = write(dir, "Greeter.java", """
                package com.example.util;
                public class Greeter {
                    void hello() {}
                }
                """);

        Graph graph = new CallSequenceParser(java.util.Set.of("com.example.util.*")).parse(file);

        assertTrue(graph.getNodes().isEmpty(), "every class of the excluded package is dropped");
    }


    // --- method references and constructors (#271) ---

    /** this::convert, processor::handle and Util::canonical are calls; they drew nothing. */
    @Test
    void methodReferencesAreCallsInSourceOrder(@TempDir Path dir) throws Exception {
        Graph graph = parser.parse(write(dir, "R.java", """
                public class R {
                    private Processor processor = new Processor();
                    void run(java.util.List<String> items) {
                        items.forEach(this::convert);
                        items.forEach(processor::handle);
                        items.forEach(Util::canonical);
                    }
                    void convert(String s) {}
                }
                class Processor { void handle(String s) {} }
                class Util { static void canonical(String s) {} }
                """));

        assertEquals(java.util.List.of("1 R.run -> R.convert", "2 R.run -> Processor.handle",
                "3 R.run -> Util.canonical"), orderedCallsFrom(graph, "R.run"));
    }

    /**
     * A constructor's body is a sequence like any method's, and new of a type declared in the
     * file is a call to its constructor; both were invisible. new of a stdlib type stays out.
     */
    @Test
    void constructorsAndInstantiationsOfFileTypesAreCalls(@TempDir Path dir) throws Exception {
        Graph graph = parser.parse(write(dir, "Order.java", """
                public class Order {
                    private final Validator validator;
                    Order(String id) {
                        java.util.List<String> notes = new java.util.ArrayList<>();
                        validator = new Validator();
                        validator.check(id);
                        audit();
                    }
                    Order(String id, int priority) { this(id); }
                    void audit() {}
                    static Order create() { return new Order("x", 1); }
                }
                class Validator { void check(String id) {} }
                """));

        assertEquals("new", graph.findNode("Order.new(String)").getLabel());
        assertEquals(java.util.List.of("1 Order.new(String) -> Validator.new",
                "2 Order.new(String) -> Validator.check", "3 Order.new(String) -> Order.audit"),
                orderedCallsFrom(graph, "Order.new(String)"));
        assertEquals(java.util.List.of("1 Order.create -> Order.new(String,int)"),
                orderedCallsFrom(graph, "Order.create"), "new picks the constructor by argument count");
    }

    /** "seq source -> target" of the CALLS edges leaving {@code source}, in sequence order. */
    private static java.util.List<String> orderedCallsFrom(Graph graph, String source) {
        return graph.getEdges().stream()
                .filter(e -> "CALLS".equals(e.getType()) && source.equals(e.getSourceId()))
                .sorted(java.util.Comparator.comparingInt(e -> Integer.parseInt(e.getLabel())))
                .map(e -> e.getLabel() + " " + e.getSourceId() + " -> " + e.getTargetId())
                .toList();
    }

    // --- receivers typed by declaration (#268) ---

    /**
     * Without symbol resolution, a call on a variable used the variable's name as the receiver,
     * so requested.isBlank() or matcher.find() drew lifelines called requested and matcher even
     * though their types (String, Matcher) are filtered as stdlib.
     */
    @Test
    void stdlibTypedVariablesAndFieldsDoNotBecomeParticipants(@TempDir Path dir) throws Exception {
        Graph graph = parser.parse(write(dir, "P.java", """
                import java.util.regex.Matcher;
                import java.util.regex.Pattern;
                public class P {
                    private static final Pattern SVG = Pattern.compile("x");
                    void run(String requested) {
                        requested.isBlank();
                        Matcher matcher = SVG.matcher(requested);
                        matcher.find();
                        for (String part : requested.split(",")) { part.isBlank(); }
                        helper();
                    }
                    void helper() {}
                }
                """));

        assertEquals(java.util.Set.of("P.run -> P.helper"), callEdges(graph),
                "only the domain call remains; String, Pattern and Matcher receivers are stdlib");
    }

    /** Three variables of one type drew three lifelines, one per variable name. */
    @Test
    void variablesOfOneTypeShareThatTypesLifeline(@TempDir Path dir) throws Exception {
        Graph graph = parser.parse(write(dir, "Q.java", """
                public class Q {
                    private Repo field = new Repo();
                    void go(Repo param) {
                        Repo local = new Repo();
                        var inferred = new Repo();
                        param.find();
                        local.find();
                        inferred.find();
                        field.find();
                    }
                }
                class Repo { void find() {} }
                """));

        // The two new Repo() inside go() are constructor calls since #271; the field initialiser's is not in a method.
        assertEquals(java.util.Set.of("Q.go -> Repo.find", "Q.go -> Repo.new"), callEdges(graph));
        assertEquals(6, graph.getEdges().size(), "one CALLS edge per call: four to Repo.find, two to Repo.new");
        for (String var : java.util.List.of("param", "local", "inferred", "field")) {
            assertNull(graph.findNode(var + ".find"), var + " must not become a participant");
        }
    }

    /** An untyped lambda parameter must not borrow the type of a field that happens to share its name. */
    @Test
    void anUntypedLambdaParameterIsNotGivenAFieldsType(@TempDir Path dir) throws Exception {
        Graph graph = parser.parse(write(dir, "L.java", """
                public class L {
                    private Repo repo = new Repo();
                    void go(java.util.List<Other> others) {
                        others.forEach(repo -> repo.touch());
                    }
                }
                class Repo { void touch() {} }
                class Other { void touch() {} }
                """));

        assertFalse(callEdges(graph).contains("L.go -> Repo.touch"),
                "the lambda's repo is an Other, not the Repo field");
    }

    private static java.util.Set<String> callEdges(Graph graph) {
        return graph.getEdges().stream()
                .filter(e -> "CALLS".equals(e.getType()))
                .map(e -> e.getSourceId() + " -> " + e.getTargetId())
                .collect(java.util.stream.Collectors.toSet());
    }

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
