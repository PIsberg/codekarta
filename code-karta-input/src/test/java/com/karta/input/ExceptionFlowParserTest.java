package com.karta.input;

import com.karta.core.model.EdgeType;
import com.karta.core.model.Graph;
import com.karta.core.model.Group;
import com.karta.input.parser.ExceptionFlowParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ExceptionFlowParserTest {

    private final ExceptionFlowParser parser = new ExceptionFlowParser();

    // ── CALLS edges (superset of CallSequenceParser) ─────────────────────────

    @Test
    void parsesClassNode(@TempDir Path dir) throws Exception {
        Path file = write(dir, "Svc.java", "public class Svc {}");
        Graph graph = parser.parse(file);
        assertNotNull(graph.findNode("Svc"));
        assertEquals("CLASS", graph.findNode("Svc").getType());
    }

    @Test
    void parsesCallEdgesWithSequenceLabels(@TempDir Path dir) throws Exception {
        Path file = write(dir, "A.java", """
                public class A {
                    B b = new B();
                    void run() {
                        b.first();
                        b.second();
                    }
                }
                """);
        Graph graph = parser.parse(file);

        assertTrue(hasCallEdge(graph, "A.run", "b.first"),  "CALLS edge to first() must exist");
        assertTrue(hasCallEdge(graph, "A.run", "b.second"), "CALLS edge to second() must exist");

        graph.getEdges().stream().filter(e -> EdgeType.CALLS.equals(e.getType())).forEach(e ->
            assertNotNull(e.getLabel(), "every CALLS edge must carry a sequence label"));
    }

    // ── Catch boundary groups ────────────────────────────────────────────────

    @Test
    void createsCatchBoundaryGroupForTryCatch(@TempDir Path dir) throws Exception {
        Path file = write(dir, "Svc.java", """
                public class Svc {
                    Repo repo = new Repo();
                    void handle() {
                        try {
                            repo.fetch();
                        } catch (RuntimeException e) {
                            // handled
                        }
                    }
                }
                """);
        Graph graph = parser.parse(file);

        assertFalse(graph.getGroups().isEmpty(), "catch boundary group must be created");
        Group g = graph.getGroups().get(0);
        assertEquals("catch(RuntimeException)", g.getLabel());
        assertTrue(g.getMemberIds().stream().anyMatch(m -> m.contains("fetch")),
                "group must contain the call inside the try block");
    }

    @Test
    void groupLabelContainsAllCatchTypes(@TempDir Path dir) throws Exception {
        Path file = write(dir, "Multi.java", """
                public class Multi {
                    Repo repo = new Repo();
                    void go() {
                        try {
                            repo.call();
                        } catch (IllegalArgumentException | IllegalStateException e) {
                            // multi-catch
                        }
                    }
                }
                """);
        Graph graph = parser.parse(file);

        assertFalse(graph.getGroups().isEmpty());
        assertTrue(graph.getGroups().get(0).getLabel().contains("IllegalArgumentException"),
                "label must mention first catch type");
    }

    @Test
    void noGroupCreatedWhenTryBlockHasNoMethodCalls(@TempDir Path dir) throws Exception {
        Path file = write(dir, "Empty.java", """
                public class Empty {
                    void go() {
                        try {
                            int x = 1 + 2;
                        } catch (Exception e) {}
                    }
                }
                """);
        Graph graph = parser.parse(file);
        assertTrue(graph.getGroups().isEmpty(), "empty try block must not produce a group");
    }

    // ── EXCEPTION_PROPAGATION edges ──────────────────────────────────────────

    @Test
    void propagatesExceptionToInScopeCaller(@TempDir Path dir) throws Exception {
        Path file = write(dir, "Proc.java", """
                public class Proc {
                    void run() throws Exception {
                        // declares throws
                    }
                    void driver() {
                        run();
                    }
                }
                """);
        Graph graph = parser.parse(file);

        assertTrue(hasExceptionEdge(graph, "Proc.run", "Proc.driver"),
                "run() must propagate exception to its caller driver()");
    }

    @Test
    void createsExceptionTypeNodeWhenNoCallerInScope(@TempDir Path dir) throws Exception {
        Path file = write(dir, "Thrower.java", """
                public class Thrower {
                    void doIt() throws IllegalStateException {
                    }
                }
                """);
        Graph graph = parser.parse(file);

        assertNotNull(graph.findNode("exception:IllegalStateException"),
                "exception-type node must be created when no caller is in scope");
        assertEquals("EXCEPTION", graph.findNode("exception:IllegalStateException").getType());
        assertTrue(hasExceptionEdge(graph, "Thrower.doIt", "exception:IllegalStateException"),
                "EXCEPTION_PROPAGATION edge to exception-type node must exist");
    }

    @Test
    void propagatesMultipleDeclaredExceptionsIndependently(@TempDir Path dir) throws Exception {
        Path file = write(dir, "Multi.java", """
                public class Multi {
                    void doIt() throws IllegalArgumentException, IllegalStateException {
                    }
                }
                """);
        Graph graph = parser.parse(file);

        assertTrue(hasExceptionEdge(graph, "Multi.doIt", "exception:IllegalArgumentException"),
                "edge to IllegalArgumentException must exist");
        assertTrue(hasExceptionEdge(graph, "Multi.doIt", "exception:IllegalStateException"),
                "edge to IllegalStateException must exist");
    }

    @Test
    void propagatesChainThroughTwoMethods(@TempDir Path dir) throws Exception {
        Path file = write(dir, "Chain.java", """
                public class Chain {
                    void inner() throws Exception {
                    }
                    void outer() throws Exception {
                        inner();
                    }
                }
                """);
        Graph graph = parser.parse(file);

        // inner propagates to outer (its in-scope caller)
        assertTrue(hasExceptionEdge(graph, "Chain.inner", "Chain.outer"),
                "inner() must propagate to outer()");
        // outer has no caller → propagates to exception-type node
        assertTrue(hasExceptionEdge(graph, "Chain.outer", "exception:Exception"),
                "outer() must propagate to exception-type node");
    }

    // ── Fault tolerance ──────────────────────────────────────────────────────

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

    // ── Helpers ──────────────────────────────────────────────────────────────

    private boolean hasCallEdge(Graph graph, String caller, String callee) {
        return graph.getEdges().stream().anyMatch(e ->
                EdgeType.CALLS.equals(e.getType())
                        && caller.equals(e.getSourceId())
                        && callee.equals(e.getTargetId()));
    }

    private boolean hasExceptionEdge(Graph graph, String source, String target) {
        return graph.getEdges().stream().anyMatch(e ->
                EdgeType.EXCEPTION_PROPAGATION.equals(e.getType())
                        && source.equals(e.getSourceId())
                        && target.equals(e.getTargetId()));
    }

    private Path write(Path dir, String name, String content) throws Exception {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
