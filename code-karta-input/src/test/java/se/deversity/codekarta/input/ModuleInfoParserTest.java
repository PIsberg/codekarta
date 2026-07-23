package se.deversity.codekarta.input;

import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Node;
import se.deversity.codekarta.input.parser.ModuleInfoParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class ModuleInfoParserTest {

    private final ModuleInfoParser parser = new ModuleInfoParser();

    @Test
    void parsesModuleNameAsNode(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("module-info.java");
        Files.writeString(file, "module se.deversity.codekarta.shipping {}");

        Graph graph = parser.parse(file);

        Node module = graph.findNode("se.deversity.codekarta.shipping");
        assertNotNull(module);
        assertEquals("MODULE", module.getType());
    }

    @Test
    void parsesRequiresDirectiveAsEdge(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("module-info.java");
        Files.writeString(file, """
                module se.deversity.codekarta.shipping {
                    requires java.base;
                    requires se.deversity.codekarta.core;
                }
                """);

        Graph graph = parser.parse(file);

        assertNotNull(graph.findNode("se.deversity.codekarta.shipping"), "source module node must exist");
        assertNotNull(graph.findNode("java.base"), "required module node must exist");
        assertNotNull(graph.findNode("se.deversity.codekarta.core"), "required module node must exist");

        assertTrue(hasEdge(graph, "se.deversity.codekarta.shipping", "java.base", "REQUIRES"));
        assertTrue(hasEdge(graph, "se.deversity.codekarta.shipping", "se.deversity.codekarta.core", "REQUIRES"));
    }

    @Test
    void parsesExportsDirectiveAsEdge(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("module-info.java");
        Files.writeString(file, """
                module se.deversity.codekarta.shipping {
                    exports se.deversity.codekarta.shipping.api;
                }
                """);

        Graph graph = parser.parse(file);

        assertNotNull(graph.findNode("se.deversity.codekarta.shipping.api"), "exported package node must exist");
        assertTrue(hasEdge(graph, "se.deversity.codekarta.shipping", "se.deversity.codekarta.shipping.api", "EXPORTS"));
    }

    @Test
    void returnsEmptyGraphOnMalformedInput(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("module-info.java");
        Files.writeString(file, "this is not valid java %%%");

        Graph graph = parser.parse(file);

        // Must not throw; partial/empty graph is acceptable
        assertNotNull(graph);
    }

    @Test
    void returnsEmptyGraphForNonExistentFile() {
        Graph graph = parser.parse(Path.of("does-not-exist/module-info.java"));
        assertNotNull(graph);
        assertTrue(graph.getNodes().isEmpty());
    }

    private boolean hasEdge(Graph graph, String src, String tgt, String type) {
        return graph.getEdges().stream().anyMatch(e ->
                src.equals(e.getSourceId()) && tgt.equals(e.getTargetId()) && type.equals(e.getType()));
    }
}
