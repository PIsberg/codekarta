package com.karta.input;

import com.karta.core.model.Graph;
import com.karta.input.parser.ClassDiagramParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

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

    private boolean hasEdge(Graph graph, String src, String tgt, String type) {
        return graph.getEdges().stream().anyMatch(e ->
                src.equals(e.getSourceId()) && tgt.equals(e.getTargetId()) && type.equals(e.getType()));
    }
}
