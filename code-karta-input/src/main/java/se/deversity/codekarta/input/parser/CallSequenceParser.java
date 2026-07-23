package se.deversity.codekarta.input.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Node;
import java.util.Set;
import se.deversity.vibetags.annotations.AIArchitecture;
import se.deversity.vibetags.annotations.AIContext;

import java.nio.file.Path;
import java.util.Map;
import java.util.logging.Logger;

@AIContext(
    focus = "Produces integer-labelled CALLS edges in textual call order within each method. Node IDs use 'ClassName.methodName' qualified form. These integer labels are the contract read by SequenceDiagramRenderer to order messages.",
    avoids = "Changing the CALLS edge label format — SequenceDiagramRenderer.isInteractionGraph() and orderMessages() depend on labels parsing as integers."
)
@AIArchitecture(belongsTo = "input", cannotReference = {"layout", "render", "cli"})
public class CallSequenceParser {

    private static final Logger log = Logger.getLogger(CallSequenceParser.class.getName());

    private final Set<String> customExcludes;

    public CallSequenceParser() {
        this(java.util.Collections.emptySet());
    }

    public CallSequenceParser(Set<String> customExcludes) {
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
                graph.addNodeIfAbsent(new Node(className, "CLASS", className));

                // Return-type map for resolving chained local calls, e.g. resolveLayout(x).layout(g)
                Map<String, String> returnTypes = ParserSupport.returnTypesOf(classDecl);

                classDecl.findAll(MethodDeclaration.class).forEach(method -> {
                    String qualifiedMethod = className + "." + method.getNameAsString();
                    if (FilterMatcher.matchesAny(qualifiedMethod, customExcludes) || FilterMatcher.matchesAny(method.getNameAsString(), customExcludes)) {
                        return;
                    }
                    graph.addNodeIfAbsent(new Node(qualifiedMethod, "METHOD", method.getNameAsString()));

                    int[] order = { 0 };
                    method.accept(new VoidVisitorAdapter<Void>() {
                        @Override
                        public void visit(MethodCallExpr call, Void arg) {
                            String name = call.getNameAsString();
                            if (FilterMatcher.matchesAny(name, customExcludes)) {
                                super.visit(call, null);
                                return;
                            }
                            String callee;
                            if (call.getScope().isPresent()) {
                                if (SequenceFilterUtil.shouldSkipScopedCall(name)) {
                                    super.visit(call, null);
                                    return;
                                }
                                String scopeName = resolveScope(call.getScope().get(), returnTypes, className);
                                if (scopeName == null) {
                                    super.visit(call, null);
                                    return;
                                }
                                callee = scopeName + "." + name;
                            } else {
                                callee = name;
                            }

                            if (FilterMatcher.matchesAny(callee, customExcludes)) {
                                super.visit(call, null);
                                return;
                            }
                            graph.addNodeIfAbsent(new Node(callee, "METHOD", name));
                            int seq = ++order[0];
                            Edge edge = new Edge(qualifiedMethod + "-calls-" + callee + "-" + seq,
                                    qualifiedMethod, callee, "CALLS");
                            edge.setLabel(String.valueOf(seq));
                            graph.addEdge(edge);
                            super.visit(call, null);
                        }
                    }, null);
                });
            });
        } catch (Exception e) {
            log.warning("Failed to parse " + sourceFile + ": " + e.getMessage());
        }
        return graph;
    }

    /**
     * Resolves a scope expression to a simple class/variable name.
     * Returns null when the scope is too complex to represent (skip the call).
     */
    static String resolveScope(Expression scope, Map<String, String> returnTypes, String thisClass) {
        if (scope.isNameExpr())            return scope.asNameExpr().getNameAsString();
        if (scope.isThisExpr())            return thisClass;
        if (scope.isObjectCreationExpr())  return scope.asObjectCreationExpr().getType().getNameAsString();
        if (scope.isMethodCallExpr()) {
            MethodCallExpr inner = scope.asMethodCallExpr();
            // local method call as scope: resolveLayout(x).layout(g) → use declared return type
            if (inner.getScope().isEmpty()) return returnTypes.get(inner.getNameAsString());
        }
        return null; // field access chains, cast expressions, etc. — skip
    }
}
