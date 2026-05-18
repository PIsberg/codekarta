package com.karta.input.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.modules.ModuleDeclaration;
import com.github.javaparser.ast.modules.ModuleDirective;
import com.github.javaparser.ast.modules.ModuleExportsDirective;
import com.github.javaparser.ast.modules.ModuleRequiresDirective;
import com.karta.core.model.Edge;
import com.karta.core.model.Graph;
import com.karta.core.model.Node;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public class ModuleInfoParser {

    private static final Logger log = Logger.getLogger(ModuleInfoParser.class.getName());
    private static final JavaParser PARSER = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));

    public Graph parse(Path moduleInfoFile) {
        Graph graph = new Graph();
        try {
            String source = Files.readString(moduleInfoFile);
            CompilationUnit cu = PARSER.parse(source).getResult().orElseThrow();
            cu.getModule().ifPresentOrElse(
                    module -> buildGraph(module, graph),
                    () -> log.warning("No module declaration found in: " + moduleInfoFile));
        } catch (Exception e) {
            log.warning("Failed to parse " + moduleInfoFile + ": " + e.getMessage());
        }
        return graph;
    }

    private void buildGraph(ModuleDeclaration module, Graph graph) {
        String moduleName = module.getNameAsString();
        graph.addNode(new Node(moduleName, "MODULE", moduleName));

        for (ModuleDirective directive : module.getDirectives()) {
            if (directive instanceof ModuleRequiresDirective req) {
                String required = req.getNameAsString();
                graph.addNodeIfAbsent(new Node(required, "MODULE", required));
                graph.addEdge(new Edge(
                        moduleName + "-requires-" + required,
                        moduleName, required, "REQUIRES"));
            } else if (directive instanceof ModuleExportsDirective exp) {
                String pkg = exp.getNameAsString();
                graph.addNodeIfAbsent(new Node(pkg, "PACKAGE", pkg));
                graph.addEdge(new Edge(
                        moduleName + "-exports-" + pkg,
                        moduleName, pkg, "EXPORTS"));
            }
        }
    }
}
