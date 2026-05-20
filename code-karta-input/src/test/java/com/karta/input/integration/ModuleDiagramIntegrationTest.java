package com.karta.input.integration;

import com.karta.core.model.Graph;
import com.karta.input.parser.ModuleInfoParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test: Target 1 — Module Diagram
 * Source: example-shipping-system/module-info.java
 */
class ModuleDiagramIntegrationTest {

    private static final Path MODULE_INFO = resolveExampleRoot().resolve("src/main/java/module-info.java");
    private static Graph graph;

    @BeforeAll
    static void parse() {
        assumeTrue(Files.exists(MODULE_INFO),
                "Skipping: example-shipping-system not found at " + MODULE_INFO);
        graph = new ModuleInfoParser().parse(MODULE_INFO);
    }

    @Test
    void extractsShippingModuleNode() {
        assertNotNull(graph.findNode("com.karta.shipping"),
                "com.karta.shipping module node must be present");
        assertEquals("MODULE", graph.findNode("com.karta.shipping").getType());
    }

    @Test
    void extractsRequiredModuleNodes() {
        assertNotNull(graph.findNode("java.base"),    "java.base must be present as a MODULE node");
        assertNotNull(graph.findNode("java.logging"), "java.logging must be present as a MODULE node");
    }

    @Test
    void extractsRequiresEdges() {
        assertTrue(hasEdge("com.karta.shipping", "java.base",    "REQUIRES"), "must REQUIRE java.base");
        assertTrue(hasEdge("com.karta.shipping", "java.logging", "REQUIRES"), "must REQUIRE java.logging");
    }

    @Test
    void extractsExportedPackageNodes() {
        assertNotNull(graph.findNode("com.karta.shipping.domain"), "domain package must be exported");
        assertNotNull(graph.findNode("com.karta.shipping.core"),   "core package must be exported");
        assertNotNull(graph.findNode("com.karta.shipping.state"),  "state package must be exported");
        assertEquals("PACKAGE", graph.findNode("com.karta.shipping.domain").getType());
    }

    @Test
    void extractsExportsEdges() {
        assertTrue(hasEdge("com.karta.shipping", "com.karta.shipping.domain", "EXPORTS"));
        assertTrue(hasEdge("com.karta.shipping", "com.karta.shipping.core",   "EXPORTS"));
        assertTrue(hasEdge("com.karta.shipping", "com.karta.shipping.state",  "EXPORTS"));
    }

    @Test
    void graphContainsExpectedNodeCount() {
        // 1 module + 2 required modules + 3 exported packages = 6 nodes
        assertEquals(6, graph.getNodes().size(),
                "graph must contain exactly 6 nodes: 1 own + 2 required + 3 exported");
    }

    // --- helpers ---

    private static Path resolveExampleRoot() {
        Path cwd = Path.of("").toAbsolutePath();
        Path fromModule = cwd.resolve("../example-shipping-system").normalize();
        if (Files.exists(fromModule)) return fromModule;
        return cwd.resolve("example-shipping-system").normalize();
    }

    private boolean hasEdge(String src, String tgt, String type) {
        return graph.getEdges().stream().anyMatch(e ->
                src.equals(e.getSourceId()) && tgt.equals(e.getTargetId()) && type.equals(e.getType()));
    }
}
