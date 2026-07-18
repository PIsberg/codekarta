package com.karta.input.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.karta.core.model.Edge;
import com.karta.core.model.EdgeType;
import com.karta.core.model.Graph;
import com.karta.core.model.Group;
import com.karta.core.model.Node;
import com.karta.core.model.NodeType;

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
import se.deversity.vibetags.annotations.AIArchitecture;
import se.deversity.vibetags.annotations.AIContext;

/**
 * Parses a Java source file for call sequences AND exception flow:
 *
 * <ul>
 * <li>CALLS edges — same as CallSequenceParser</li>
 * <li>One {@link Group} per try-catch block (catch boundary)</li>
 * <li>EXCEPTION_PROPAGATION edges for methods that declare checked throws</li>
 * </ul>
 *
 * Two-pass approach per class:
 * <ol>
 * <li>Build call graph + collect throws declarations + create catch-boundary
 * groups.</li>
 * <li>Walk throws declarations → emit EXCEPTION_PROPAGATION to in-scope
 * callers,
 * or to a synthetic exception-type node when no caller is found.</li>
 * </ol>
 */
@AIContext(
    focus = "Two-pass per class: (1) build call graph + collect TryStmt catch-boundaries → Group objects; (2) walk throws declarations → emit EXCEPTION_PROPAGATION edges. Exception nodes use 'exception:TypeName' id prefix for renderer detection.",
    avoids = "Merging both passes into one — Pass 2 needs the complete caller map from Pass 1 to resolve propagation targets correctly."
)
@AIArchitecture(belongsTo = "input", cannotReference = {"layout", "render", "cli"})
public class ExceptionFlowParser {

    private static final Logger log = Logger.getLogger(ExceptionFlowParser.class.getName());

    private final Set<String> customExcludes;

    public ExceptionFlowParser() {
        this(Collections.emptySet());
    }

    public ExceptionFlowParser(Set<String> customExcludes) {
        this.customExcludes = ParserSupport.normalizeExcludes(customExcludes);
    }

    public Graph parse(Path sourceFile) {
        Graph graph = new Graph();
        try {
            CompilationUnit cu = ParserSupport.parseJava21(sourceFile);

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                String className = classDecl.getNameAsString();
                if (FilterMatcher.matchesAny(className, customExcludes)) {
                    return;
                }
                graph.addNodeIfAbsent(new Node(className, NodeType.CLASS, className));

                // callee node-id → [caller method-ids] (used in pass 2)
                Map<String, List<String>> callersOf = new HashMap<>();
                // methodId → declared exception simple names
                Map<String, Set<String>> methodThrows = new LinkedHashMap<>();

                // Pre-pass: collect local method names + return types for scope resolution
                Set<String> localMethodNames = classDecl.findAll(MethodDeclaration.class).stream()
                        .map(MethodDeclaration::getNameAsString)
                        .collect(Collectors.toSet());

                Map<String, String> returnTypes = ParserSupport.returnTypesOf(classDecl);

                // ── Pass 1 ────────────────────────────────────────────────
                classDecl.findAll(MethodDeclaration.class).forEach(method -> {
                    String methodId = className + "." + method.getNameAsString();
                    if (FilterMatcher.matchesAny(methodId, customExcludes) || FilterMatcher.matchesAny(method.getNameAsString(), customExcludes)) {
                        return;
                    }
                    graph.addNodeIfAbsent(new Node(methodId, NodeType.METHOD, method.getNameAsString()));

                    // Collect declared throws
                    method.getThrownExceptions()
                            .forEach(ex -> methodThrows.computeIfAbsent(methodId, k -> new LinkedHashSet<>())
                                    .add(ex.asString()));

                    // Walk body for CALLS edges
                    int[] seq = { 0 };
                    method.accept(new VoidVisitorAdapter<Void>() {
                        @Override
                        public void visit(MethodCallExpr call, Void arg) {
                            String name = call.getNameAsString();
                            if (FilterMatcher.matchesAny(name, customExcludes)) {
                                super.visit(call, arg);
                                return;
                            }
                            String callee;
                            if (call.getScope().isPresent()) {
                                if (SequenceFilterUtil.shouldSkipScopedCall(name)) {
                                    super.visit(call, arg);
                                    return;
                                }
                                String scopeName = CallSequenceParser.resolveScope(
                                        call.getScope().get(), returnTypes, className);
                                if (scopeName == null) {
                                    super.visit(call, arg);
                                    return;
                                }
                                callee = scopeName + "." + name;
                            } else {
                                callee = localMethodNames.contains(name) ? className + "." + name : name;
                            }
                            if (FilterMatcher.matchesAny(callee, customExcludes)) {
                                super.visit(call, arg);
                                return;
                            }
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
                    ParserSupport.addCatchBoundaryGroups(graph, method, methodId, call -> {
                        String cname = call.getNameAsString();
                        if (call.getScope().isPresent()) {
                            if (SequenceFilterUtil.shouldSkipScopedCall(cname)) {
                                return null;
                            }
                            String sn = CallSequenceParser.resolveScope(
                                    call.getScope().get(), returnTypes, className);
                            return sn == null ? null : sn + "." + cname;
                        }
                        return localMethodNames.contains(cname) ? className + "." + cname : cname;
                    });
                });

                // ── Pass 2: EXCEPTION_PROPAGATION edges ───────────────────
                methodThrows.forEach((methodId, exTypes) -> exTypes.forEach(exType -> {
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
                        callers.forEach(caller -> graph.addEdge(new Edge(
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
