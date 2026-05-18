package com.karta.input;

import com.karta.core.model.Edge;
import com.karta.core.model.Graph;
import com.karta.core.model.Node;
import com.karta.input.parser.ModuleInfoParser;
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
        Files.writeString(file, "module com.karta.shipping {}");

        Graph graph = parser.parse(file);

        Node module = graph.findNode("com.karta.shipping");
        assertNotNull(module);
        assertEquals("MODULE", module.getType());
    }

    @Test
    void parsesRequiresDirectiveAsEdge(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("module-info.java");
        Files.writeString(file, """
                module com.karta.shipping {
                    requires java.base;
                    requires com.karta.core;
                }
                """);

        Graph graph = parser.parse(file);

        assertNotNull(graph.findNode("com.karta.shipping"), "source module node must exist");
        assertNotNull(graph.findNode("java.base"), "required module node must exist");
        assertNotNull(graph.findNode("com.karta.core"), "required module node must exist");

        assertTrue(hasEdge(graph, "com.karta.shipping", "java.base", "REQUIRES"));
        assertTrue(hasEdge(graph, "com.karta.shipping", "com.karta.core", "REQUIRES"));
    }

    @Test
    void parsesExportsDirectiveAsEdge(@TempDir Path dir) throws Exception {
        Path file = dir.resolve("module-info.java");
        Files.writeString(file, """
                module com.karta.shipping {
                    exports com.karta.shipping.api;
                }
                """);

        Graph graph = parser.parse(file);

        assertNotNull(graph.findNode("com.karta.shipping.api"), "exported package node must exist");
        assertTrue(hasEdge(graph, "com.karta.shipping", "com.karta.shipping.api", "EXPORTS"));
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
