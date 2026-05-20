package com.karta.input;

import com.karta.core.model.Graph;
import com.karta.input.parser.ClassDiagramParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import se.deversity.vibetags.annotations.AIParallelTests;

import static org.junit.jupiter.api.Assertions.*;
import static com.karta.input.parser.ClassDiagramParser.rawType;
import static com.karta.input.parser.ClassDiagramParser.innerGenericType;

@AIParallelTests
class ClassDiagramParserTest {

    private final ClassDiagramParser parser = new ClassDiagramParser();

    @Test
    void parsesInterfaceNode(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Flyable.java"), """
                public interface Flyable {
                    void fly();
                }
                """);

        Graph graph = parser.parse(dir);

        assertNotNull(graph.findNode("Flyable"));
        assertEquals("INTERFACE", graph.findNode("Flyable").getType());
    }

    @Test
    void parsesClassNode(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Vehicle.java"), "public class Vehicle {}");

        Graph graph = parser.parse(dir);

        assertNotNull(graph.findNode("Vehicle"));
        assertEquals("CLASS", graph.findNode("Vehicle").getType());
    }

    @Test
    void parsesExtendsEdge(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Animal.java"), "public class Animal {}");
        Files.writeString(dir.resolve("Dog.java"), "public class Dog extends Animal {}");

        Graph graph = parser.parse(dir);

        assertTrue(hasEdge(graph, "Dog", "Animal", "EXTENDS"));
    }

    @Test
    void parsesImplementsEdge(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Runnable.java"), "public interface Runnable {}");
        Files.writeString(dir.resolve("Thread.java"), "public class Thread implements Runnable {}");

        Graph graph = parser.parse(dir);

        assertTrue(hasEdge(graph, "Thread", "Runnable", "IMPLEMENTS"));
    }

    @Test
    void parsesInheritanceChain(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Shape.java"), "public interface Shape { double area(); }");
        Files.writeString(dir.resolve("Polygon.java"), "public class Polygon implements Shape { public double area() { return 0; } }");
        Files.writeString(dir.resolve("Rectangle.java"), "public class Rectangle extends Polygon {}");

        Graph graph = parser.parse(dir);

        assertNotNull(graph.findNode("Shape"), "Shape interface must be present");
        assertNotNull(graph.findNode("Polygon"), "Polygon class must be present");
        assertNotNull(graph.findNode("Rectangle"), "Rectangle class must be present");
        assertTrue(hasEdge(graph, "Polygon", "Shape", "IMPLEMENTS"));
        assertTrue(hasEdge(graph, "Rectangle", "Polygon", "EXTENDS"));
    }

    @Test
    void parsesHasEdgeForDomainField(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Engine.java"), "public class Engine {}");
        Files.writeString(dir.resolve("Car.java"), """
                public class Car {
                    private Engine engine;
                }
                """);

        Graph graph = parser.parse(dir);

        assertTrue(hasEdge(graph, "Car", "Engine", "HAS"),
                "Car should have a HAS edge to Engine");
    }

    @Test
    void skipsStringFieldType(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Person.java"), """
                public class Person {
                    private String name;
                }
                """);

        Graph graph = parser.parse(dir);

        assertNull(graph.findNode("String"), "String type should not produce a node");
        assertFalse(hasEdge(graph, "Person", "String", "HAS"));
    }

    @Test
    void returnsEmptyGraphForEmptyDirectory(@TempDir Path dir) {
        Graph graph = parser.parse(dir);
        assertNotNull(graph);
        assertTrue(graph.getNodes().isEmpty());
    }

    @Test
    void toleratesMalformedSourceFile(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Broken.java"), "this is %%% not java");
        Files.writeString(dir.resolve("Good.java"), "public class Good {}");

        Graph graph = parser.parse(dir);

        // Must not throw; should still parse Good.java
        assertNotNull(graph.findNode("Good"));
    }

    @Test
    void skipsGenericCollectionFieldType(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Order.java"), """
                import java.util.List;
                public class Order {
                    private List<String> items;
                }
                """);

        Graph graph = parser.parse(dir);

        assertNull(graph.findNode("List<String>"), "Parameterised List should not produce a node");
        assertNull(graph.findNode("List"), "Bare List should be skipped (stdlib)");
        assertFalse(hasEdge(graph, "Order", "List<String>", "HAS"));
    }

    @Test
    void hasEdgeLabelEqualsFieldName(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Engine.java"), "public class Engine {}");
        Files.writeString(dir.resolve("Car.java"), """
                public class Car {
                    private Engine engine;
                }
                """);

        Graph graph = parser.parse(dir);

        var hasEdge = graph.getEdges().stream()
                .filter(e -> "Car".equals(e.getSourceId()) && "Engine".equals(e.getTargetId())
                        && "HAS".equals(e.getType()))
                .findFirst();
        assertTrue(hasEdge.isPresent(), "HAS edge must exist");
        assertEquals("engine", hasEdge.get().getLabel(), "HAS edge label must be the field name");
    }

    @Test
    void nodePropertiesContainFieldsAndMethods(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Widget.java"), """
                public class Widget {
                    private String name;
                    public String getName() { return name; }
                }
                """);

        Graph graph = parser.parse(dir);

        var node = graph.findNode("Widget");
        assertNotNull(node);
        assertNotNull(node.getProperties());
        assertNotNull(node.getProperties().get("fields"), "fields property must be populated");
        assertTrue(node.getProperties().get("fields").contains("name"), "fields should list 'name'");
        assertNotNull(node.getProperties().get("methods"), "methods property must be populated");
        assertTrue(node.getProperties().get("methods").contains("getName"), "methods should list 'getName'");
    }

    @Test
    void rawTypeStripsGenericParams() {
        assertEquals("List", rawType("List<Node>"));
        assertEquals("Map", rawType("Map<String,String>"));
        assertEquals("Foo", rawType("Foo"));
        assertEquals("Foo", rawType("Foo<A,B,C>"));
    }

    @Test
    void parsesHasEdgeForDomainTypeInGenericCollection(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Item.java"), "public class Item {}");
        Files.writeString(dir.resolve("Cart.java"), """
                import java.util.List;
                public class Cart {
                    private List<Item> items;
                }
                """);

        Graph graph = parser.parse(dir);

        assertTrue(hasEdge(graph, "Cart", "Item", "HAS"),
                "Cart should have a HAS edge to Item via List<Item>");
        assertNotNull(graph.findNode("Item"), "Item domain node must be present");
        var hasEdge = graph.getEdges().stream()
                .filter(e -> "Cart".equals(e.getSourceId()) && "Item".equals(e.getTargetId())
                        && "HAS".equals(e.getType()))
                .findFirst();
        assertTrue(hasEdge.isPresent());
        assertEquals("items", hasEdge.get().getLabel(), "HAS edge label must be the field name");
    }

    @Test
    void skipsStdlibInnerGenericType(@TempDir Path dir) throws Exception {
        // List<String> — String is in SKIP_TYPES, must not produce a node
        Files.writeString(dir.resolve("Names.java"), """
                import java.util.List;
                public class Names {
                    private List<String> values;
                }
                """);

        Graph graph = parser.parse(dir);

        assertNull(graph.findNode("String"), "String should not produce a node from List<String>");
        assertFalse(hasEdge(graph, "Names", "String", "HAS"));
    }

    @Test
    void innerGenericTypeHelperExtractsParam() {
        assertEquals("Node",   innerGenericType("List<Node>"));
        assertEquals("Node",   innerGenericType("Set<Node>"));
        assertEquals("Edge",   innerGenericType("Map<String, Edge>"));
        assertEquals("String", innerGenericType("Map<String, String>"));
        assertNull(innerGenericType("Node"));
        assertNull(innerGenericType("String"));
    }

    @Test
    void constantsClassGetsStereotypeProperty(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Status.java"), """
                public final class Status {
                    public static final String ACTIVE   = "ACTIVE";
                    public static final String INACTIVE = "INACTIVE";
                    private Status() {}
                }
                """);

        Graph graph = parser.parse(dir);

        var node = graph.findNode("Status");
        assertNotNull(node);
        String stereotype = node.getProperties() != null ? node.getProperties().get("stereotype") : null;
        assertEquals("«constants»", stereotype,
                "Constants-only class must carry «constants» stereotype property");
    }

    private boolean hasEdge(Graph graph, String src, String tgt, String type) {
        return graph.getEdges().stream().anyMatch(e ->
                src.equals(e.getSourceId()) && tgt.equals(e.getTargetId()) && type.equals(e.getType()));
    }
}
