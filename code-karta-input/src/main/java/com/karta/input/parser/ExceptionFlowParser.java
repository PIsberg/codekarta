package com.karta.input.parser;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.karta.core.model.Edge;
import com.karta.core.model.EdgeType;
import com.karta.core.model.Graph;
import com.karta.core.model.Group;
import com.karta.core.model.Node;
import com.karta.core.model.NodeType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.logging.Logger;

/**
 * Parses a Java source file for call sequences AND exception flow:
 *
 * <ul>
 *   <li>CALLS edges — same as CallSequenceParser</li>
 *   <li>One {@link Group} per try-catch block (catch boundary)</li>
 *   <li>EXCEPTION_PROPAGATION edges for methods that declare checked throws</li>
 * </ul>
 *
 * Two-pass approach per class:
 * <ol>
 *   <li>Build call graph + collect throws declarations + create catch-boundary groups.</li>
 *   <li>Walk throws declarations → emit EXCEPTION_PROPAGATION to in-scope callers,
 *       or to a synthetic exception-type node when no caller is found.</li>
 * </ol>
 */
public class ExceptionFlowParser {

    private static final Logger log = Logger.getLogger(ExceptionFlowParser.class.getName());

    public Graph parse(Path sourceFile) {
        Graph graph = new Graph();
        try {
            CompilationUnit cu = StaticJavaParser.parse(Files.readString(sourceFile));

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                String className = classDecl.getNameAsString();
                graph.addNodeIfAbsent(new Node(className, NodeType.CLASS, className));

                // callee node-id → [caller method-ids]  (used in pass 2)
                Map<String, List<String>> callersOf    = new HashMap<>();
                // methodId → declared exception simple names
                Map<String, Set<String>>  methodThrows = new LinkedHashMap<>();

                // Pre-pass: collect local method names so unscoped calls resolve to qualified ids
                Set<String> localMethodNames = classDecl.findAll(MethodDeclaration.class).stream()
                        .map(MethodDeclaration::getNameAsString)
                        .collect(Collectors.toSet());

                // ── Pass 1 ────────────────────────────────────────────────
                classDecl.findAll(MethodDeclaration.class).forEach(method -> {
                    String methodId = className + "." + method.getNameAsString();
                    graph.addNodeIfAbsent(new Node(methodId, NodeType.METHOD, method.getNameAsString()));

                    // Collect declared throws
                    method.getThrownExceptions().forEach(ex ->
                        methodThrows.computeIfAbsent(methodId, k -> new LinkedHashSet<>())
                                    .add(ex.asString()));

                    // Walk body for CALLS edges
                    int[] seq = {0};
                    method.accept(new VoidVisitorAdapter<Void>() {
                        @Override
                        public void visit(MethodCallExpr call, Void arg) {
                            String name = call.getNameAsString();
                            String callee = call.getScope()
                                    .map(s -> s.toString() + "." + name)
                                    .orElse(localMethodNames.contains(name) ? className + "." + name : name);

                            graph.addNodeIfAbsent(new Node(callee, NodeType.METHOD, call.getNameAsString()));

                            int n = ++seq[0];
                            Edge edge = new Edge(methodId + "-calls-" + callee + "-" + n,
                                    methodId, callee, EdgeType.CALLS);
                            edge.setLabel(String.valueOf(n));
                            graph.addEdge(edge);

                            callersOf.computeIfAbsent(callee, k -> new ArrayList<>()).add(methodId);
                            super.visit(call, arg);
                        }
                    }, null);

                    // Walk for try-catch → catch-boundary groups
                    int[] tryIdx = {0};
                    method.findAll(TryStmt.class).forEach(tryStmt -> {
                        String catchTypes = tryStmt.getCatchClauses().stream()
                                .map(c -> c.getParameter().getType().asString())
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("Exception");

                        Group group = new Group(
                                "catch-boundary-" + methodId + "-" + tryIdx[0]++,
                                "catch(" + catchTypes + ")");

                        tryStmt.getTryBlock().findAll(MethodCallExpr.class).forEach(call -> {
                            String cname = call.getNameAsString();
                            String callee = call.getScope()
                                    .map(s -> s.toString() + "." + cname)
                                    .orElse(localMethodNames.contains(cname) ? className + "." + cname : cname);
                            graph.addNodeIfAbsent(new Node(callee, NodeType.METHOD, cname));
                            group.addMember(callee);
                        });

                        if (!group.getMemberIds().isEmpty()) {
                            graph.addGroup(group);
                        }
                    });
                });

                // ── Pass 2: EXCEPTION_PROPAGATION edges ───────────────────
                methodThrows.forEach((methodId, exTypes) ->
                    exTypes.forEach(exType -> {
                        // Callers may have recorded this method by its simple name (no-scope call)
                        String simpleName = methodId.contains(".")
                                ? methodId.substring(methodId.lastIndexOf('.') + 1)
                                : methodId;

                        List<String> callers = new ArrayList<>(
                                callersOf.getOrDefault(methodId, Collections.emptyList()));
                        callersOf.getOrDefault(simpleName, Collections.emptyList()).stream()
                                .filter(c -> !callers.contains(c))
                                .forEach(callers::add);

                        if (callers.isEmpty()) {
                            // No in-scope caller — edge to a synthetic exception-type node
                            String exNodeId = "exception:" + exType;
                            graph.addNodeIfAbsent(new Node(exNodeId, NodeType.EXCEPTION, exType));
                            graph.addEdge(new Edge(
                                    methodId + "-propagates-" + exType,
                                    methodId, exNodeId, EdgeType.EXCEPTION_PROPAGATION));
                        } else {
                            callers.forEach(caller ->
                                graph.addEdge(new Edge(
                                        methodId + "-propagates-to-" + caller,
                                        methodId, caller, EdgeType.EXCEPTION_PROPAGATION)));
                        }
                    }));
            });

        } catch (Exception e) {
            log.warning("Failed to parse " + sourceFile + ": " + e.getMessage());
        }
        return graph;
    }
}
