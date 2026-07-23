package se.deversity.codekarta.core.model;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GroupTest {

    @Test
    void constructorSetsFields() {
        Group group = new Group("g1", "se.deversity.codekarta.domain");
        assertEquals("g1", group.getId());
        assertEquals("se.deversity.codekarta.domain", group.getLabel());
        assertNotNull(group.getMemberIds());
        assertTrue(group.getMemberIds().isEmpty());
    }

    @Test
    void addMemberAppendsMemberId() {
        Group group = new Group("g1", "pkg");
        group.addMember("n1");
        group.addMember("n2");
        assertEquals(2, group.getMemberIds().size());
        assertTrue(group.getMemberIds().contains("n1"));
        assertTrue(group.getMemberIds().contains("n2"));
    }

    @Test
    void propertiesCanBeSet() {
        Group group = new Group("g1", "module");
        group.getProperties().put("version", "1.0");
        assertEquals("1.0", group.getProperties().get("version"));
    }

    @Test
    void jsonRoundTrip() throws Exception {
        Group original = new Group("g1", "se.deversity.codekarta");
        original.addMember("ClassA");
        original.addMember("ClassB");
        ObjectMapper mapper = new ObjectMapper();
        String json = mapper.writeValueAsString(original);
        Group restored = mapper.readValue(json, Group.class);
        assertEquals(original.getId(), restored.getId());
        assertEquals(original.getLabel(), restored.getLabel());
        assertEquals(original.getMemberIds(), restored.getMemberIds());
    }
}
