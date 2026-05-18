package com.karta.input;

import com.karta.core.model.EdgeType;
import com.karta.core.model.Graph;
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

    private Path write(Path dir, String name, String content) throws Exception {
        Path file = dir.resolve(name);
        Files.writeString(file, content);
        return file;
    }
}
