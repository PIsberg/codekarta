package se.deversity.codekarta.input.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
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
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

@AIContext(
    focus = "Produces integer-labelled CALLS edges in textual call order within each method. Node IDs use 'ClassName.methodName' qualified form, with the parameter types appended, 'ClassName.methodName(String,int)', when the class declares that name more than once (MethodIds). These integer labels are the contract read by SequenceDiagramRenderer to order messages.",
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
            Set<String> externalTypes = SequenceFilterUtil.externalTypeNames(cu);
            ParserSupport.forEachIncludedClass(cu, customExcludes, graph, (classDecl, className) -> {

                // Return-type map for resolving chained local calls, e.g. resolveLayout(x).layout(g)
                Map<String, String> returnTypes = ParserSupport.returnTypesOf(classDecl);
                Map<String, TypeDeclaration<?>> fileTypes = typesByName(cu);

                // Direct methods only: findAll would reach into nested and anonymous classes and
                // draw their methods on this class's lifeline (#265). Calls made inside an
                // anonymous class or lambda still count, for the method that declares it.
                classDecl.getMethods().forEach(method -> {
                    String qualifiedMethod = MethodIds.of(className, classDecl, method);
                    if (FilterMatcher.matchesAny(className + "." + method.getNameAsString(), customExcludes)
                            || FilterMatcher.matchesAny(qualifiedMethod, customExcludes) || FilterMatcher.matchesAny(method.getNameAsString(), customExcludes)) {
                        return;
                    }
                    graph.addNodeIfAbsent(new Node(qualifiedMethod, "METHOD", method.getNameAsString()));
                    Map<String, String> varTypes = receiverTypes(classDecl, method, returnTypes);

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
                                String scopeName = resolveScope(call.getScope().get(), returnTypes, className, varTypes);
                                if (scopeName == null || externalTypes.contains(scopeName)) {
                                    super.visit(call, null);
                                    return;
                                }
                                callee = calleeIn(scopeName, call, fileTypes);
                            } else if (returnTypes.containsKey(name)) {
                                // local method call — qualify so it joins this class's lifeline
                                callee = MethodIds.forLocalCall(className, classDecl, call);
                            } else {
                                // unscoped non-local call (static import etc.) — not attributable
                                super.visit(call, null);
                                return;
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
    static String resolveScope(Expression scope, Map<String, String> returnTypes, String thisClass,
                               Map<String, String> varTypes) {
        if (scope.isNameExpr()) {
            String name = scope.asNameExpr().getNameAsString();
            if (!varTypes.containsKey(name)) {
                return name; // not a variable in scope: a type name (static call) or unknown
            }
            String type = varTypes.get(name);
            if (type == null) {
                return name; // a variable whose type cannot be read (untyped lambda parameter)
            }
            return type.endsWith("[]") ? null : type; // arrays have no domain methods
        }
        if (scope.isThisExpr())            return thisClass;
        if (scope.isObjectCreationExpr())  return scope.asObjectCreationExpr().getType().getNameAsString();
        if (scope.isMethodCallExpr()) {
            MethodCallExpr inner = scope.asMethodCallExpr();
            // local method call as scope: resolveLayout(x).layout(g) → use declared return type
            if (inner.getScope().isEmpty()) return returnTypes.get(inner.getNameAsString());
        }
        return null; // field access chains, cast expressions, etc. — skip
    }

    /**
     * Variable name → declared raw simple type for every field of {@code classDecl} (record
     * components included) and every parameter and local of {@code method}, catch and for-each
     * variables included. Locals win over fields of the same name. A {@code var} takes the type
     * of {@code new T(...)} or of a local method it is assigned from; a variable whose type
     * cannot be read (an untyped lambda parameter, an unresolvable {@code var}) maps to
     * {@code null}, so it is drawn by name rather than mistaken for a same-named field.
     *
     * <p>Without this the receiver of {@code matcher.find()} was the string {@code matcher}: it
     * escaped the stdlib filter, which knows type names, and every variable of one type drew a
     * lifeline of its own (issue #268).
     */
    static Map<String, String> receiverTypes(TypeDeclaration<?> classDecl, MethodDeclaration method,
                                             Map<String, String> returnTypes) {
        Map<String, String> types = new HashMap<>();
        if (classDecl instanceof RecordDeclaration record) {
            record.getParameters().forEach(p -> types.put(p.getNameAsString(), MethodIds.simpleType(p.getType())));
        }
        classDecl.getFields().forEach(field -> field.getVariables().forEach(v ->
                types.put(v.getNameAsString(), declaredType(v, returnTypes))));
        method.findAll(Parameter.class).forEach(p -> types.put(p.getNameAsString(),
                p.getType().isUnknownType() ? null
                        : MethodIds.simpleType(p.getType()) + (p.isVarArgs() ? "[]" : "")));
        method.findAll(VariableDeclarator.class).forEach(v ->
                types.put(v.getNameAsString(), declaredType(v, returnTypes)));
        return types;
    }

    private static String declaredType(VariableDeclarator variable, Map<String, String> returnTypes) {
        if (!variable.getType().isVarType()) {
            return MethodIds.simpleType(variable.getType());
        }
        Expression init = variable.getInitializer().orElse(null);
        if (init != null && init.isObjectCreationExpr()) {
            return init.asObjectCreationExpr().getType().getNameAsString();
        }
        if (init != null && init.isMethodCallExpr() && init.asMethodCallExpr().getScope().isEmpty()) {
            return returnTypes.get(init.asMethodCallExpr().getNameAsString());
        }
        return null;
    }

    /** Every class, interface, record and enum in the file, by simple name. */
    static Map<String, TypeDeclaration<?>> typesByName(CompilationUnit cu) {
        Map<String, TypeDeclaration<?>> types = new HashMap<>();
        ParserSupport.methodBearingTypes(cu).forEach(t -> types.putIfAbsent(t.getNameAsString(), t));
        return types;
    }

    /**
     * The callee id for a call on a receiver of type {@code scopeName}. When that type is declared
     * in this file, the id names the overload the way the declaration's own node does
     * ({@link MethodIds}); otherwise it is {@code scopeName.name}.
     */
    static String calleeIn(String scopeName, MethodCallExpr call, Map<String, TypeDeclaration<?>> fileTypes) {
        TypeDeclaration<?> declared = fileTypes.get(scopeName);
        String id = declared == null ? null : MethodIds.forLocalCall(scopeName, declared, call);
        return id != null ? id : scopeName + "." + call.getNameAsString();
    }
}
