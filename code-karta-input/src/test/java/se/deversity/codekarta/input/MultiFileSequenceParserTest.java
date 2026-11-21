package se.deversity.codekarta.input;

import se.deversity.codekarta.core.model.EdgeType;
import se.deversity.codekarta.core.model.Graph;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class MultiFileSequenceParserTest {

    private final MultiFileSequenceParser parser = new MultiFileSequenceParser();

    @Test
    void parsesNodesFromMultipleFiles(@TempDir Path sourceRoot) throws Exception {
        write(sourceRoot, "Service.java", "public class Service { void run() {} }");
        write(sourceRoot, "Repo.java",    "public class Repo    { void find() {} }");

        Graph graph = parser.parse(sourceRoot);

        assertNotNull(graph.findNode("Service"), "Service class node must exist");
        assertNotNull(graph.findNode("Repo"),    "Repo class node must exist");
    }

    @Test
    void stitchesCallEdgeAcrossFiles(@TempDir Path sourceRoot) throws Exception {
        write(sourceRoot, "Repo.java", """
                public class Repo {
                    public void find() {}
                }
                """);
        write(sourceRoot, "Service.java", """
                public class Service {
                    private Repo repo = new Repo();
                    public void run() {
                        repo.find();
                    }
                }
                """);

        Graph graph = parser.parse(sourceRoot);

        // Symbol solver resolves repo.find() → Repo.find; if resolution fails,
        // fall-back produces repo.find — both are valid stitched outcomes.
        boolean stitched = graph.getEdges().stream()
                .filter(e -> EdgeType.CALLS.equals(e.getType()))
                .filter(e -> "Service.run".equals(e.getSourceId()))
                .anyMatch(e -> e.getTargetId().contains("find"));

        assertTrue(stitched, "Service.run must have a CALLS edge targeting Repo.find (resolved or scope-based)");
    }

    @Test
    void callEdgesCarrySequenceLabel(@TempDir Path sourceRoot) throws Exception {
        write(sourceRoot, "A.java", """
                public class A {
                    B b = new B();
                    void go() { b.first(); b.second(); }
                }
                """);
        write(sourceRoot, "B.java", """
                public class B {
                    public void first()  {}
                    public void second() {}
                }
                """);

        Graph graph = parser.parse(sourceRoot);

        long labeled = graph.getEdges().stream()
                .filter(e -> EdgeType.CALLS.equals(e.getType()) && "A.go".equals(e.getSourceId()))
                .filter(e -> e.getLabel() != null)
                .count();

        assertEquals(2, labeled, "both CALLS edges from A.go must carry sequence labels");
    }

    @Test
    void skipsModuleInfoJava(@TempDir Path sourceRoot) throws Exception {
        write(sourceRoot, "module-info.java", "module com.test {}");
        write(sourceRoot, "App.java",         "public class App {}");

        Graph graph = parser.parse(sourceRoot);

        assertNull(graph.findNode("module"), "module-info.java must be excluded");
        assertNotNull(graph.findNode("App"),  "App.java must still be parsed");
    }

    @Test
    void returnsEmptyGraphForEmptyDirectory(@TempDir Path sourceRoot) {
        Graph graph = parser.parse(sourceRoot);
        assertNotNull(graph);
        assertTrue(graph.getNodes().isEmpty());
    }

    @Test
    void returnsEmptyGraphForNonExistentDirectory() {
        Graph graph = parser.parse(Path.of("no-such-dir-xyzzy"));
        assertNotNull(graph);
        assertTrue(graph.getNodes().isEmpty());
    }

    @Test
    void excludesTypesAndMethods(@TempDir Path sourceRoot) throws Exception {
        write(sourceRoot, "A.java", """
                public class A {
                    B b = new B();
                    void go() { b.first(); b.second(); }
                }
                """);
        write(sourceRoot, "B.java", """
                public class B {
                    public void first()  {}
                    public void second() {}
                }
                """);

        MultiFileSequenceParser customParser = new MultiFileSequenceParser(java.util.Set.of("B.second"));
        Graph graph = customParser.parse(sourceRoot);

        assertNotNull(graph.findNode("A.go"));
        assertNotNull(graph.findNode("B.first"));
        assertNull(graph.findNode("B.second"), "B.second should be excluded");
    }

    @Test
    void limitsCallSequenceDepth(@TempDir Path sourceRoot) throws Exception {
        write(sourceRoot, "A.java", """
                public class A {
                    B b = new B();
                    void go() { b.first(); }
                }
                """);
        write(sourceRoot, "B.java", """
                public class B {
                    C c = new C();
                    public void first()  { c.second(); }
                }
                """);
        write(sourceRoot, "C.java", """
                public class C {
                    public void second() {}
                }
                """);

        MultiFileSequenceParser customParser = new MultiFileSequenceParser(java.util.Set.of(), 1);
        Graph graph = customParser.parse(sourceRoot);

        assertNotNull(graph.findNode("A.go"));
        assertNotNull(graph.findNode("B.first"));
        assertNull(graph.findNode("C.second"), "C.second should be pruned at depth 2");
    }

    private Path write(Path dir, String name, String content) throws Exception {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    @Test
    void repositoryRootDoesNotStitchTestCallers(@TempDir Path repo) throws Exception {
        Files.writeString(repo.resolve("pom.xml"), "<project/>");
        Path main = Files.createDirectories(repo.resolve("src/main/java"));
        Path test = Files.createDirectories(repo.resolve("src/test/java"));
        write(main, "Repo.java", "public class Repo { public void find() {} }");
        write(main, "Service.java", """
                public class Service {
                    private Repo repo = new Repo();
                    public void run() { repo.find(); }
                }
                """);
        write(test, "ServiceTest.java", """
                public class ServiceTest {
                    void runs() { new Service().run(); }
                }
                """);

        Graph production = parser.parse(repo);
        Graph withTests = new MultiFileSequenceParser(java.util.Set.of(), Integer.MAX_VALUE, true).parse(repo);

        assertNotNull(production.findNode("Service"));
        assertNull(production.findNode("ServiceTest"), "test callers must not be stitched by default");
        assertNotNull(withTests.findNode("ServiceTest"), "includeTests must stitch test callers");
    }

    @Test
    void methodsDeclaredInRecordsAndEnumsTakePartInTheSequence(@TempDir Path sourceRoot) throws Exception {
        write(sourceRoot, "Priced.java", """
                public interface Priced { int price(); }
                """);
        write(sourceRoot, "Customer.java", """
                public class Customer { String name; }
                """);
        write(sourceRoot, "Order.java", """
                public record Order(Customer customer, Status status) implements Priced {
                    public int price() { return customer.hashCode() + helper(); }
                    int helper() { return Calc.twice(2); }
                }
                """);
        write(sourceRoot, "Status.java", """
                public enum Status implements Priced { NEW, PAID; public int price() { return Calc.twice(1); } }
                """);
        write(sourceRoot, "Calc.java", """
                public class Calc { static int twice(int x) { return 2*x; }
                    public static class Inner { Customer owner; } }
                """);
        write(sourceRoot, "Service.java", """
                public class Service { Calc calc; int run(Order o) { return o.price() + Calc.twice(3); } }
                """);

        Graph graph = parser.parse(sourceRoot);

        java.util.Set<String> calls = graph.getEdges().stream()
                .filter(e -> EdgeType.CALLS.equals(e.getType()))
                .map(e -> e.getSourceId() + " -> " + e.getTargetId())
                .collect(java.util.stream.Collectors.toSet());
        assertEquals(java.util.Set.of(
                "Service.run -> Order.price",
                "Service.run -> Calc.twice",
                "Order.price -> Order.helper",
                "Order.helper -> Calc.twice",
                "Status.price -> Calc.twice"), calls);
        assertNotNull(graph.findNode("Order"), "a record is a participant");
        assertNotNull(graph.findNode("Status"), "an enum is a participant");
    }

    // --- recursion and cycles under --max-depth (#229) ---

    private static java.util.Set<String> callEdges(Graph graph) {
        return graph.getEdges().stream()
                .filter(e -> EdgeType.CALLS.equals(e.getType()))
                .map(e -> e.getSourceId() + " -> " + e.getTargetId())
                .collect(java.util.stream.Collectors.toSet());
    }

    @Test
    void maxDepthKeepsTheCallTreeOfARecursiveEntryMethod(@TempDir Path sourceRoot) throws Exception {
        write(sourceRoot, "Walker.java", """
                public class Walker {
                    void walk(int n) { visit(n); if (n > 0) walk(n - 1); }
                    void visit(int n) { Helper.log(n); }
                }
                """);
        write(sourceRoot, "Helper.java", "public class Helper { static void log(int n) { } }");

        Graph graph = new MultiFileSequenceParser(java.util.Set.of(), 3).parse(sourceRoot);

        assertEquals(java.util.Set.of("Walker.walk -> Walker.visit", "Walker.walk -> Walker.walk",
                "Walker.visit -> Helper.log"), callEdges(graph), "the real depth is 2, so depth 3 keeps everything");
    }

    @Test
    void maxDepthCountsFromTheFirstMethodOfACycleWithNoOutsideCaller(@TempDir Path sourceRoot) throws Exception {
        write(sourceRoot, "A.java", "public class A { static void ping() { B.pong(); } }");
        write(sourceRoot, "B.java", "public class B { static void pong() { A.ping(); C.log(); } }");
        write(sourceRoot, "C.java", "public class C { static void log() { D.sink(); } }");
        write(sourceRoot, "D.java", "public class D { static void sink() { } }");

        Graph graph = new MultiFileSequenceParser(java.util.Set.of(), 2).parse(sourceRoot);

        assertEquals(java.util.Set.of("A.ping -> B.pong", "B.pong -> A.ping", "B.pong -> C.log"), callEdges(graph),
                "A.ping is depth 0, B.pong 1, C.log 2; D.sink at depth 3 is pruned rather than restarting at 0");
    }

    @Test
    void anInterfaceIsTypedInterface(@TempDir Path sourceRoot) throws Exception {
        write(sourceRoot, "Priced.java", "public interface Priced { int price(); }");
        write(sourceRoot, "Order.java", "public class Order implements Priced { public int price() { return 1; } }");

        Graph graph = parser.parse(sourceRoot);

        assertEquals("INTERFACE", graph.findNode("Priced").getType());
        assertEquals("CLASS", graph.findNode("Order").getType());
    }
}
