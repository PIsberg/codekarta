package com.karta.input.integration;

import com.karta.core.model.Graph;
import com.karta.core.model.Node;
import com.karta.input.parser.ClassDiagramParser;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

/**
 * Integration test: Target 2 — Class Diagram
 * Source: example-shipping-system/src/com/karta/shipping/domain/
 */
class ClassDiagramIntegrationTest {

    private static final Path DOMAIN_DIR = resolveExampleRoot()
            .resolve("src/main/java/com/karta/shipping/domain");
    private static Graph graph;

    @BeforeAll
    static void parse() {
        assumeTrue(Files.exists(DOMAIN_DIR),
                "Skipping: domain directory not found at " + DOMAIN_DIR);
        graph = new ClassDiagramParser().parse(DOMAIN_DIR);
    }

    // --- Node type assertions ---

    @Test
    void shippingUnitIsInterface() {
        Node node = graph.findNode("ShippingUnit");
        assertNotNull(node, "ShippingUnit node must be present");
        assertEquals("INTERFACE", node.getType(), "ShippingUnit must be typed INTERFACE");
    }

    @Test
    void cargoIsClass() {
        Node node = graph.findNode("Cargo");
        assertNotNull(node, "Cargo node must be present");
        assertEquals("CLASS", node.getType(), "Cargo must be typed CLASS");
    }

    @Test
    void expressCargoIsClass() {
        Node node = graph.findNode("ExpressCargo");
        assertNotNull(node, "ExpressCargo node must be present");
        assertEquals("CLASS", node.getType(), "ExpressCargo must be typed CLASS");
    }

    // --- Relationship assertions ---

    @Test
    void cargoImplementsShippingUnit() {
        assertTrue(hasEdge("Cargo", "ShippingUnit", "IMPLEMENTS"),
                "Cargo must have an IMPLEMENTS edge to ShippingUnit");
    }

    @Test
    void expressCargoExtendsCargo() {
        assertTrue(hasEdge("ExpressCargo", "Cargo", "EXTENDS"),
                "ExpressCargo must have an EXTENDS edge to Cargo");
    }

    @Test
    void noSpuriousInheritanceEdges() {
        long extendsCount = graph.getEdges().stream()
                .filter(e -> "EXTENDS".equals(e.getType())).count();
        long implementsCount = graph.getEdges().stream()
                .filter(e -> "IMPLEMENTS".equals(e.getType())).count();
        assertEquals(1, extendsCount,   "exactly one EXTENDS edge expected");
        assertEquals(1, implementsCount, "exactly one IMPLEMENTS edge expected");
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
