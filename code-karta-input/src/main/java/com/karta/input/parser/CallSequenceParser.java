package com.karta.input.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.karta.core.model.Edge;
import com.karta.core.model.Graph;
import com.karta.core.model.Node;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.logging.Logger;

public class CallSequenceParser {

    private static final Logger log = Logger.getLogger(CallSequenceParser.class.getName());
    private static final JavaParser PARSER = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_17));

    public Graph parse(Path sourceFile) {
        Graph graph = new Graph();
        try {
            String source = Files.readString(sourceFile);
            CompilationUnit cu = PARSER.parse(source).getResult().orElseThrow();

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                String className = classDecl.getNameAsString();
                graph.addNodeIfAbsent(new Node(className, "CLASS", className));

                classDecl.findAll(MethodDeclaration.class).forEach(method -> {
                    String qualifiedMethod = className + "." + method.getNameAsString();
                    graph.addNodeIfAbsent(new Node(qualifiedMethod, "METHOD", method.getNameAsString()));

                    int[] order = { 0 };
                    method.accept(new VoidVisitorAdapter<Void>() {
                        @Override
                        public void visit(MethodCallExpr call, Void arg) {
                            String callee = call.getScope()
                                    .map(s -> s.toString() + "." + call.getNameAsString())
                                    .orElse(call.getNameAsString());

                            graph.addNodeIfAbsent(new Node(callee, "METHOD", call.getNameAsString()));

                            int seq = ++order[0];
                            Edge edge = new Edge(
                                    qualifiedMethod + "-calls-" + callee + "-" + seq,
                                    qualifiedMethod, callee, "CALLS");
                            edge.setLabel(String.valueOf(seq));
                            graph.addEdge(edge);

                            super.visit(call, arg);
                        }
                    }, null);
                });
            });
        } catch (Exception e) {
            log.warning("Failed to parse " + sourceFile + ": " + e.getMessage());
        }
        return graph;
    }
}
