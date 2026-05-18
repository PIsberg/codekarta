package com.karta.input.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.karta.core.model.Edge;
import com.karta.core.model.Graph;
import com.karta.core.model.Node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;

public class ClassDiagramParser {

    private static final Logger log = Logger.getLogger(ClassDiagramParser.class.getName());
    private static final JavaParser PARSER = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));

    private static final Set<String> SKIP_TYPES = Set.of(
            "int", "long", "double", "float", "boolean", "byte", "short", "char",
            "String", "Object", "Integer", "Long", "Double", "Float", "Boolean",
            "Byte", "Short", "Character", "List", "Map", "Set", "Queue",
            "Optional", "Stream", "Collection", "Iterable", "void", "Void",
            "StringBuilder", "StringBuffer", "Number", "Comparable", "Serializable");

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
                String fieldType = field.getElementType().asString();
                if (!SKIP_TYPES.contains(fieldType) && Character.isUpperCase(fieldType.charAt(0))) {
                    String fieldName = field.getVariables().get(0).getNameAsString();
                    graph.addNodeIfAbsent(new Node(fieldType, "CLASS", fieldType));
                    graph.addEdge(new Edge(
                            name + "-has-" + fieldName + "-" + fieldType,
                            name, fieldType, "HAS"));
                }
            }
        }
    }
}
