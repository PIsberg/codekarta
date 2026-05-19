package com.karta.input.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.karta.core.model.Edge;
import com.karta.core.model.Graph;
import com.karta.core.model.Node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

@se.deversity.vibetags.annotations.AIContext(
    focus = "Generic-type stripping: rawType() must be called before SKIP_TYPES lookup so 'List<Node>' → 'List' and gets filtered. Node.properties is populated with truncated field/method summaries for UML compartments. HAS edge labels carry the field name.",
    avoids = "Bypassing SKIP_TYPES for stdlib types — class diagrams quickly become unreadable with List/Map/String nodes. Populating Node.properties for externally-referenced stub nodes (only populate for types whose source is in this parse run)."
)
@se.deversity.vibetags.annotations.AIArchitecture(belongsTo = "input", cannotReference = {"layout", "render", "cli"})
public class ClassDiagramParser {

    private static final Logger log = Logger.getLogger(ClassDiagramParser.class.getName());
    private static final JavaParser PARSER = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));

    private static final Set<String> SKIP_TYPES = Set.of(
            "int", "long", "double", "float", "boolean", "byte", "short", "char",
            "String", "Object", "Integer", "Long", "Double", "Float", "Boolean",
            "Byte", "Short", "Character", "List", "Map", "Set", "Queue",
            "Optional", "Stream", "Collection", "Iterable", "void", "Void",
            "StringBuilder", "StringBuffer", "Number", "Comparable", "Serializable");

    private static final int MAX_MEMBERS = 6;

    public Graph parse(Path sourceDirectory) {
        Graph graph = new Graph();
        try (var stream = Files.walk(sourceDirectory)) {
            List<Path> javaFiles = stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();
            for (Path file : javaFiles) {
                parseFile(file, graph);
            }
        } catch (IOException e) {
            log.warning("Error walking directory " + sourceDirectory + ": " + e.getMessage());
        }
        return graph;
    }

    private void parseFile(Path file, Graph graph) {
        try {
            String source = Files.readString(file);
            CompilationUnit cu = PARSER.parse(source).getResult().orElseThrow();
            for (TypeDeclaration<?> type : cu.getTypes()) {
                processType(type, graph);
            }
        } catch (Exception e) {
            log.warning("Failed to parse " + file + ": " + e.getMessage());
        }
    }

    private void processType(TypeDeclaration<?> type, Graph graph) {
        String name = type.getNameAsString();
        String nodeType = (type instanceof ClassOrInterfaceDeclaration coid && coid.isInterface())
                ? "INTERFACE"
                : "CLASS";

        graph.addNodeIfAbsent(new Node(name, nodeType, name));
        // Update the stored node (may have been added as a stub by another file's reference)
        Node typeNode = graph.findNode(name);
        if (typeNode != null) {
            typeNode.setType(nodeType);
            typeNode.setProperties(buildProperties(type));
        }

        if (type instanceof ClassOrInterfaceDeclaration coid) {
            for (var extended : coid.getExtendedTypes()) {
                String parent = extended.getNameAsString();
                graph.addNodeIfAbsent(new Node(parent, "CLASS", parent));
                graph.addEdge(new Edge(name + "-extends-" + parent, name, parent, "EXTENDS"));
            }
            for (var implemented : coid.getImplementedTypes()) {
                String iface = implemented.getNameAsString();
                graph.addNodeIfAbsent(new Node(iface, "INTERFACE", iface));
                graph.addEdge(new Edge(name + "-implements-" + iface, name, iface, "IMPLEMENTS"));
            }
            for (FieldDeclaration field : coid.getFields()) {
                // Strip generic params before filtering: List<Node> → List, Map<K,V> → Map
                String rawType = rawType(field.getElementType().asString());
                if (!SKIP_TYPES.contains(rawType) && Character.isUpperCase(rawType.charAt(0))) {
                    String fieldName = field.getVariables().get(0).getNameAsString();
                    graph.addNodeIfAbsent(new Node(rawType, "CLASS", rawType));
                    Edge hasEdge = new Edge(
                            name + "-has-" + fieldName + "-" + rawType,
                            name, rawType, "HAS");
                    hasEdge.setLabel(fieldName);
                    graph.addEdge(hasEdge);
                }
            }
        }
    }

    private Map<String, String> buildProperties(TypeDeclaration<?> type) {
        Map<String, String> props = new HashMap<>();

        List<String> fieldLines = new ArrayList<>();
        for (FieldDeclaration field : type.getFields()) {
            String displayType = field.getElementType().asString();
            for (var v : field.getVariables()) {
                fieldLines.add(v.getNameAsString() + ": " + displayType);
            }
        }
        if (!fieldLines.isEmpty()) {
            props.put("fields", formatList(fieldLines));
        }

        if (type instanceof ClassOrInterfaceDeclaration coid) {
            List<String> methodLines = new ArrayList<>();
            for (MethodDeclaration m : coid.getMethods()) {
                StringBuilder sig = new StringBuilder(m.getNameAsString()).append("(");
                var params = m.getParameters();
                for (int i = 0; i < params.size(); i++) {
                    if (i > 0) sig.append(", ");
                    sig.append(params.get(i).getType().asString());
                }
                sig.append("): ").append(m.getType().asString());
                methodLines.add(sig.toString());
            }
            if (!methodLines.isEmpty()) {
                props.put("methods", formatList(methodLines));
            }
        }

        return props;
    }

    private static String formatList(List<String> lines) {
        if (lines.size() <= MAX_MEMBERS) {
            return String.join("\n", lines);
        }
        int extra = lines.size() - MAX_MEMBERS;
        List<String> truncated = new ArrayList<>(lines.subList(0, MAX_MEMBERS));
        truncated.add("…(+" + extra + " more)");
        return String.join("\n", truncated);
    }

    /** Strip generic type parameters: {@code List<Node>} → {@code List}. */
    public static String rawType(String typeName) {
        int lt = typeName.indexOf('<');
        return lt >= 0 ? typeName.substring(0, lt) : typeName;
    }
}
