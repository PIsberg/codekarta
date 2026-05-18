package com.karta.core.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NodeTest {

    @Test
    void constructorSetsFields() {
        Node node = new Node("n1", "CLASS", "MyClass");
        assertEquals("n1", node.getId());
        assertEquals("CLASS", node.getType());
        assertEquals("MyClass", node.getLabel());
        assertNotNull(node.getProperties());
        assertTrue(node.getProperties().isEmpty());
    }

    @Test
    void defaultConstructorProducesNullFields() {
        Node node = new Node();
        assertNull(node.getId());
        assertNull(node.getX());
    }

    @Test
    void layoutCoordinatesAreNullBeforeLayout() {
        Node node = new Node("n1", "CLASS", "MyClass");
        assertNull(node.getX());
        assertNull(node.getY());
        assertNull(node.getWidth());
        assertNull(node.getHeight());
    }

    @Test
    void layoutCoordinatesCanBeSet() {
        Node node = new Node("n1", "CLASS", "MyClass");
        node.setX(10.0);
        node.setY(20.0);
        node.setWidth(150.0);
        node.setHeight(50.0);
        assertEquals(10.0, node.getX());
        assertEquals(20.0, node.getY());
        assertEquals(150.0, node.getWidth());
        assertEquals(50.0, node.getHeight());
    }

    @Test
    void propertiesCanBeAdded() {
        Node node = new Node("n1", "CLASS", "MyClass");
        node.getProperties().put("visibility", "public");
        assertEquals("public", node.getProperties().get("visibility"));
    }

    @Test
    void jsonSerializationOmitsNullFields() throws Exception {
        Node node = new Node("n1", "CLASS", "MyClass");
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(node);
        assertFalse(json.contains("\"x\""), "null x should be omitted");
        assertTrue(json.contains("\"id\":\"n1\""));
    }

    @Test
    void jsonRoundTrip() throws Exception {
        Node original = new Node("n1", "CLASS", "MyClass");
        original.setX(10.0);
        original.setY(20.0);
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(original);
        Node restored = mapper.readValue(json, Node.class);
        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getX(), restored.getX());
        assertEquals(original.getY(), restored.getY());
    }
}
