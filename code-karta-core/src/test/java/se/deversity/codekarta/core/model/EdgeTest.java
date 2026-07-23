package se.deversity.codekarta.core.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EdgeTest {

    @Test
    void constructorSetsFields() {
        Edge edge = new Edge("e1", "A", "B", "EXTENDS");
        assertEquals("e1", edge.getId());
        assertEquals("A", edge.getSourceId());
        assertEquals("B", edge.getTargetId());
        assertEquals("EXTENDS", edge.getType());
        assertNull(edge.getLabel());
    }

    @Test
    void labelCanBeSet() {
        Edge edge = new Edge("e1", "A", "B", "CALLS");
        edge.setLabel("1");
        assertEquals("1", edge.getLabel());
    }

    @Test
    void jsonSerializationOmitsNullLabel() throws Exception {
        Edge edge = new Edge("e1", "A", "B", "EXTENDS");
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(edge);
        assertFalse(json.contains("\"label\""), "null label should be omitted");
        assertTrue(json.contains("\"type\":\"EXTENDS\""));
    }

    @Test
    void jsonRoundTrip() throws Exception {
        Edge original = new Edge("e1", "src", "tgt", "IMPLEMENTS");
        original.setLabel("impl");
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(original);
        Edge restored = mapper.readValue(json, Edge.class);
        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getSourceId(), restored.getSourceId());
        assertEquals(original.getTargetId(), restored.getTargetId());
        assertEquals(original.getType(), restored.getType());
        assertEquals(original.getLabel(), restored.getLabel());
    }
}
