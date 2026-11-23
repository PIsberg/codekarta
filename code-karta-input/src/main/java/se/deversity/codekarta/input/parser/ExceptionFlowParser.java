package se.deversity.codekarta.input.parser;

import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.CallableDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.expr.MethodReferenceExpr;
import com.github.javaparser.ast.expr.ObjectCreationExpr;
import com.github.javaparser.ast.stmt.TryStmt;
import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.EdgeType;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Group;
import se.deversity.codekarta.core.model.Node;
import se.deversity.codekarta.core.model.NodeType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
            Set<String> externalTypes = SequenceFilterUtil.externalTypeNames(cu);

            ParserSupport.forEachIncludedClass(cu, customExcludes, graph, (classDecl, className) -> {

                // callee node-id → [caller method-ids] (used in pass 2)
                Map<String, List<String>> callersOf = new HashMap<>();
                // methodId → declared exception simple names
                Map<String, Set<String>> methodThrows = new LinkedHashMap<>();

                Map<String, String> returnTypes = ParserSupport.returnTypesOf(classDecl);
                Map<String, TypeDeclaration<?>> fileTypes = CallSequenceParser.typesByName(cu);

                // ── Pass 1 ────────────────────────────────────────────────
                // Direct methods and constructors only, as in CallSequenceParser (#265, #271).
                MethodIds.callables(classDecl).forEach(method -> {
                    String methodId = MethodIds.of(className, classDecl, method);
                    String label = MethodIds.labelOf(method);
                    if (FilterMatcher.matchesAny(className + "." + label, customExcludes)
                            || FilterMatcher.matchesAny(methodId, customExcludes) || FilterMatcher.matchesAny(label, customExcludes)) {
                        return;
                    }
                    graph.addNodeIfAbsent(new Node(methodId, NodeType.METHOD, label));
                    SingleFileCallees callees = new SingleFileCallees(className, classDecl, returnTypes,
                            CallSequenceParser.receiverTypes(classDecl, method, returnTypes),
                            fileTypes, externalTypes, customExcludes);

                    // Collect declared throws
                    method.getThrownExceptions()
                            .forEach(ex -> methodThrows.computeIfAbsent(methodId, k -> new LinkedHashSet<>())
                                    .add(ex.asString()));

                    // Walk body for CALLS edges
                    int[] seq = { 0 };
                    SingleFileCallees.forEachCall(method, callees, callee -> {
                        graph.addNodeIfAbsent(new Node(callee.id(), NodeType.METHOD, callee.label()));
                        int n = ++seq[0];
                        Edge edge = new Edge(methodId + "-calls-" + callee.id() + "-" + n,
                                methodId, callee.id(), EdgeType.CALLS);
                        edge.setLabel(String.valueOf(n));
                        graph.addEdge(edge);
                        callersOf.computeIfAbsent(callee.id(), k -> new ArrayList<>()).add(methodId);
                    });

                    // Walk for try-catch → catch-boundary groups
                    addCatchBoundaryGroups(graph, method, methodId, expr -> {
                        SingleFileCallees.Callee callee = callees.of(expr);
                        return callee == null ? null : callee.id();
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

    /**
     * Adds one catch-boundary {@link Group} per try-statement in {@code method}, with a member per
     * call inside the try block, and the method itself as {@link Group#OWNER_PROPERTY}. Without
     * the owner the renderer could only select messages by callee, and a frame stretched over
     * every call to a shared helper from anywhere in the diagram (issue #267).
     * {@code calleeResolver} maps a call to its node id, or returns {@code null} to skip it.
     */
    private static void addCatchBoundaryGroups(Graph graph, CallableDeclaration<?> method, String methodId,
            java.util.function.Function<Expression, String> calleeResolver) {
        int[] tryIdx = { 0 };
        method.findAll(TryStmt.class).forEach(tryStmt -> {
            String catchTypes = tryStmt.getCatchClauses().stream()
                    .map(c -> c.getParameter().getType().asString())
                    .reduce((a, b) -> a + ", " + b)
                    .orElse("Exception");

            Group group = new Group(
                    "catch-boundary-" + methodId + "-" + tryIdx[0]++,
                    "catch(" + catchTypes + ")");
            group.getProperties().put(Group.OWNER_PROPERTY, methodId);

            tryStmt.getTryBlock().findAll(Expression.class, e -> e instanceof MethodCallExpr
                    || e instanceof MethodReferenceExpr || e instanceof ObjectCreationExpr).forEach(call -> {
                String callee = calleeResolver.apply(call);
                // Only reference nodes the parser already accepted — creating nodes here would
                // bypass its project/stdlib filters and leak JDK types or raw variable names into
                // the diagram as participants.
                if (callee == null || graph.findNode(callee) == null) {
                    return;
                }
                group.addMember(callee);
            });

            if (!group.getMemberIds().isEmpty()) {
                graph.addGroup(group);
            }
        });
    }
}
