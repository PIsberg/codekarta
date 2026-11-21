package se.deversity.codekarta.input;

import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.input.parser.ClassDiagramParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;


import static org.junit.jupiter.api.Assertions.*;
import static se.deversity.codekarta.input.parser.ClassDiagramParser.rawType;
import static se.deversity.codekarta.input.parser.ClassDiagramParser.innerGenericType;

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

    @Test
    void parsesPackageGroup(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Status.java"), """
                package se.deversity.codekarta.shipping;
                public class Status {}
                """);

        Graph graph = parser.parse(dir);

        assertEquals(1, graph.getGroups().size(), "There should be one package group");
        var group = graph.getGroups().get(0);
        assertEquals("package-se.deversity.codekarta.shipping", group.getId());
        assertEquals("se.deversity.codekarta.shipping", group.getLabel());
        assertEquals(java.util.List.of("se.deversity.codekarta.shipping.Status"), group.getMemberIds(),
                "a packaged type's node id is its qualified name");
        assertEquals("Status", graph.findNode("se.deversity.codekarta.shipping.Status").getLabel());
    }

    @Test
    void parsesWithCustomExcludes(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Engine.java"), "public class Engine {}");
        Files.writeString(dir.resolve("Car.java"), """
                public class Car {
                    private Engine engine;
                    private String notes;
                }
                """);

        ClassDiagramParser customParser = new ClassDiagramParser(java.util.Set.of("Engine"));
        Graph graph = customParser.parse(dir);

        assertNotNull(graph.findNode("Car"));
        assertNull(graph.findNode("Engine"), "Engine class should be excluded");
        assertFalse(hasEdge(graph, "Car", "Engine", "HAS"), "Car should not have HAS edge to Engine");
    }

    // ------------------------------------------------------------------ member cap

    @Test
    void capsCompartmentMembersAtSixByDefault(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Wide.java"), wideClass());

        Graph graph = new ClassDiagramParser().parse(dir);

        String methods = graph.findNode("Wide").getProperties().get("methods");
        assertEquals(7, methods.split("\n").length, "six members plus the summary line");
        assertTrue(methods.contains("…(+4 more)"), "the remainder must be counted, not silently dropped");
    }

    @Test
    void raisesTheCapWhenAskedTo(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Wide.java"), wideClass());

        Graph graph = new ClassDiagramParser(java.util.Set.of(), 8).parse(dir);

        String methods = graph.findNode("Wide").getProperties().get("methods");
        assertEquals(9, methods.split("\n").length, "eight members plus the summary line");
        assertTrue(methods.contains("…(+2 more)"));
    }

    @Test
    void showsEveryMemberWhenTheCapIsDisabled(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Wide.java"), wideClass());

        Graph graph = new ClassDiagramParser(
                java.util.Set.of(), ClassDiagramParser.UNLIMITED_MEMBERS).parse(dir);

        String methods = graph.findNode("Wide").getProperties().get("methods");
        assertEquals(10, methods.split("\n").length);
        assertFalse(methods.contains("more)"), "nothing was truncated, so nothing to report");
    }

    @Test
    void aNegativeCapIsTreatedAsUnlimitedRatherThanAsAnError(@TempDir Path dir) throws Exception {
        Files.writeString(dir.resolve("Wide.java"), wideClass());

        Graph graph = new ClassDiagramParser(java.util.Set.of(), -1).parse(dir);

        assertEquals(10, graph.findNode("Wide").getProperties().get("methods").split("\n").length);
    }

    /** Ten methods — enough to be truncated by the default cap and by a raised one. */
    private String wideClass() {
        StringBuilder sb = new StringBuilder("public class Wide {\n");
        for (int i = 0; i < 10; i++) {
            sb.append("    void m").append(i).append("() {}\n");
        }
        return sb.append("}\n").toString();
    }

    private boolean hasEdge(Graph graph, String src, String tgt, String type) {
        return graph.getEdges().stream().anyMatch(e ->
                src.equals(e.getSourceId()) && tgt.equals(e.getTargetId()) && type.equals(e.getType()));
    }

    // --- repository root as input ---

    private static void writeAt(Path root, String relative, String content) throws Exception {
        Path file = root.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
    }

    @Test
    void repositoryRootLeavesOutTestsAndBuildOutput(@TempDir Path repo) throws Exception {
        writeAt(repo, "pom.xml", "<project/>");
        writeAt(repo, "src/main/java/app/Order.java", "package app; public class Order {}");
        writeAt(repo, "src/test/java/app/OrderTest.java", "package app; class OrderTest { Order order; }");
        writeAt(repo, "target/generated-sources/app/OrderMapper.java", "package app; class OrderMapper {}");

        Graph graph = parser.parse(repo);

        assertNotNull(graph.findNode("app.Order"), "production class must be drawn");
        assertNull(graph.findNode("app.OrderTest"), "test sources must not be drawn by default");
        assertNull(graph.findNode("app.OrderMapper"), "generated sources under target/ must not be drawn");
    }

    @Test
    void includeTestsDrawsTestSourcesToo(@TempDir Path repo) throws Exception {
        writeAt(repo, "pom.xml", "<project/>");
        writeAt(repo, "src/main/java/app/Order.java", "package app; public class Order {}");
        writeAt(repo, "src/test/java/app/OrderTest.java", "package app; class OrderTest { Order order; }");

        Graph graph = new ClassDiagramParser(java.util.Set.of(), ClassDiagramParser.DEFAULT_MAX_MEMBERS, true)
                .parse(repo);

        assertNotNull(graph.findNode("app.Order"));
        assertNotNull(graph.findNode("app.OrderTest"), "includeTests must bring test sources back");
    }

    // --- types that share a simple name across packages (#226) ---

    @Test
    void typesWithTheSameSimpleNameInDifferentPackagesStaySeparate(@TempDir Path dir) throws Exception {
        writeAt(dir, "a/Config.java", "package a; public class Config { int timeout; String host; }");
        writeAt(dir, "b/Config.java", "package b; public class Config { boolean verbose; }");
        writeAt(dir, "b/User.java", "package b; public class User { Config config; }");

        Graph graph = parser.parse(dir);

        assertEquals(3, graph.getNodes().size(), "a.Config, b.Config and b.User are three types");
        var aConfig = graph.findNode("a.Config");
        var bConfig = graph.findNode("b.Config");
        assertNotNull(aConfig);
        assertNotNull(bConfig);
        assertEquals("Config", aConfig.getLabel(), "the label stays the simple name");
        assertEquals("timeout: int\nhost: String", aConfig.getProperties().get("fields"));
        assertEquals("verbose: boolean", bConfig.getProperties().get("fields"));
        assertTrue(hasEdge(graph, "b.User", "b.Config", "HAS"), "a same-package reference resolves to its own package");
        assertFalse(hasEdge(graph, "b.User", "a.Config", "HAS"));
        assertEquals(java.util.List.of("a.Config"), groupMembers(graph, "a"));
        assertEquals(java.util.List.of("b.Config", "b.User"), groupMembers(graph, "b"));
    }

    @Test
    void anImportDecidesWhichSameNamedTypeAReferenceMeans(@TempDir Path dir) throws Exception {
        writeAt(dir, "a/Config.java", "package a; public class Config {}");
        writeAt(dir, "a/Base.java", "package a; public class Base {}");
        writeAt(dir, "b/Config.java", "package b; public class Config {}");
        writeAt(dir, "b/Base.java", "package b; public class Base {}");
        writeAt(dir, "c/Service.java", """
                package c;
                import a.Config;
                import b.*;
                public class Service extends Base {
                    Config config;
                    java.util.List<Config> history;
                }
                """);

        Graph graph = parser.parse(dir);

        assertTrue(hasEdge(graph, "c.Service", "a.Config", "HAS"), "a single-type import wins");
        assertFalse(hasEdge(graph, "c.Service", "b.Config", "HAS"),
                "a single-type import shadows the same name from an on-demand import");
        assertTrue(hasEdge(graph, "c.Service", "b.Base", "EXTENDS"), "an on-demand import resolves the supertype");
        assertEquals(2, graph.getEdges().stream()
                .filter(e -> "HAS".equals(e.getType()) && "a.Config".equals(e.getTargetId())).count(),
                "both the plain field and the generic container point at the imported type");
    }

    @Test
    void aFullyQualifiedReferenceResolvesWithoutAnImport(@TempDir Path dir) throws Exception {
        writeAt(dir, "a/Config.java", "package a; public class Config {}");
        writeAt(dir, "b/Config.java", "package b; public class Config {}");
        writeAt(dir, "c/Service.java", "package c; public class Service { b.Config config; }");

        Graph graph = parser.parse(dir);

        assertTrue(hasEdge(graph, "c.Service", "b.Config", "HAS"));
        assertFalse(hasEdge(graph, "c.Service", "a.Config", "HAS"));
    }

    @Test
    void anUnambiguousSimpleNameStillResolvesAcrossPackages(@TempDir Path dir) throws Exception {
        // No import: the source would not compile, but diagrams are drawn from partial trees too,
        // and a name only one type carries can only mean that type.
        writeAt(dir, "a/Engine.java", "package a; public class Engine {}");
        writeAt(dir, "b/Car.java", "package b; public class Car { Engine engine; }");

        Graph graph = parser.parse(dir);

        assertTrue(hasEdge(graph, "b.Car", "a.Engine", "HAS"));
    }

    @Test
    void anAmbiguousSimpleNameDrawsNoEdgeRatherThanAGuess(@TempDir Path dir) throws Exception {
        writeAt(dir, "a/Config.java", "package a; public class Config {}");
        writeAt(dir, "b/Config.java", "package b; public class Config {}");
        writeAt(dir, "c/Service.java", "package c; public class Service { Config config; }");

        Graph graph = parser.parse(dir);

        assertEquals(java.util.List.of(), graph.getEdges(), "neither Config is more likely than the other");
    }

    @Test
    void sameNamedInterfacesInDifferentPackagesGetTheirOwnImplementsEdges(@TempDir Path dir) throws Exception {
        writeAt(dir, "a/Handler.java", "package a; public interface Handler {}");
        writeAt(dir, "b/Handler.java", "package b; public interface Handler {}");
        writeAt(dir, "a/AImpl.java", "package a; public class AImpl implements Handler {}");
        writeAt(dir, "b/BImpl.java", "package b; public class BImpl implements Handler {}");

        Graph graph = parser.parse(dir);

        assertTrue(hasEdge(graph, "a.AImpl", "a.Handler", "IMPLEMENTS"));
        assertTrue(hasEdge(graph, "b.BImpl", "b.Handler", "IMPLEMENTS"));
        assertEquals("INTERFACE", graph.findNode("a.Handler").getType());
        assertEquals("INTERFACE", graph.findNode("b.Handler").getType());
    }

    @Test
    void aDeclaredSupertypeIsDrawnEvenWhenItsNameIsAStdlibName(@TempDir Path dir) throws Exception {
        writeAt(dir, "u/List.java", "package u; public interface List<E> {}");
        writeAt(dir, "u/ArrayList.java", "package u; public class ArrayList<E> implements List<E> { List<E> self; }");

        Graph graph = parser.parse(dir);

        assertTrue(hasEdge(graph, "u.ArrayList", "u.List", "IMPLEMENTS"),
                "the stdlib skip-list is for field types; a supertype declared in this run is drawn");
        assertFalse(hasEdge(graph, "u.ArrayList", "u.List", "HAS"), "a List-typed field is still skipped");
    }

    private static java.util.List<String> groupMembers(Graph graph, String packageName) {
        return graph.getGroups().stream()
                .filter(g -> packageName.equals(g.getLabel()))
                .findFirst()
                .map(g -> g.getMemberIds())
                .orElseThrow();
    }
}
