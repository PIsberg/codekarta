package se.deversity.codekarta.input;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.EdgeType;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Group;
import se.deversity.codekarta.core.model.Node;
import se.deversity.codekarta.core.model.NodeType;
import se.deversity.codekarta.input.parser.FilterMatcher;
import se.deversity.codekarta.input.parser.MethodIds;
import se.deversity.codekarta.input.parser.ParserSupport;
import se.deversity.codekarta.input.parser.SourceTree;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import se.deversity.vibetags.annotations.AIArchitecture;
import se.deversity.vibetags.annotations.AIContext;
import se.deversity.vibetags.annotations.AIKeepInSync;

/**
 * Parses multiple Java source files and stitches their call graphs into a
 * single unified {@link Graph}.
 *
 * <p>
 * Cross-file method calls are resolved via JavaParser's
 * {@link JavaSymbolSolver}: when a callee's declaring type can be determined,
 * the callee node id uses the qualified form {@code ClassName.methodName} (with a parameter-type
 * suffix for an overloaded name, see {@link MethodIds}) so
 * nodes from different files are automatically linked. Unresolvable calls fall
 * back gracefully to scope-based naming.
 *
 * <p>
 * Exception-flow annotations (catch-boundary groups, EXCEPTION_PROPAGATION
 * edges) are omitted; use {@code sequenceOnly=false} via
 * {@link JavaSourceInputParser} on individual files when full exception flow is
 * needed per-file.
 */
@AIContext(
    focus = "Cross-file call resolution via JavaSymbolSolver: resolved callees get 'ClassName.methodName' ids (MethodIds adds a parameter-type suffix for overloads) so nodes from different source files are automatically linked. Unresolvable calls fall back to scope-based naming without crashing.",
    avoids = "Adding exception-flow parsing here — catch-boundary groups and EXCEPTION_PROPAGATION edges belong to ExceptionFlowParser on individual files, not to the multi-file stitching pass."
)
@AIArchitecture(belongsTo = "input", cannotReference = {"layout", "render", "cli"})
public class MultiFileSequenceParser {

    private static final Logger log = Logger.getLogger(MultiFileSequenceParser.class.getName());

    private final Set<String> customExcludes;
    private final int maxDepth;
    private final boolean includeTests;

    public MultiFileSequenceParser() {
        this(java.util.Collections.emptySet(), Integer.MAX_VALUE);
    }

    public MultiFileSequenceParser(Set<String> customExcludes) {
        this(customExcludes, Integer.MAX_VALUE);
    }

    public MultiFileSequenceParser(Set<String> customExcludes, int maxDepth) {
        this(customExcludes, maxDepth, false);
    }

    /**
     * @param includeTests {@code true} to stitch callers in test source sets too when the input
     *                     is a project or repository root; see {@link se.deversity.codekarta.input.parser.SourceTree}
     */
    public MultiFileSequenceParser(Set<String> customExcludes, int maxDepth, boolean includeTests) {
        this.customExcludes = ParserSupport.normalizeExcludes(customExcludes);
        this.maxDepth = maxDepth > 0 ? maxDepth : Integer.MAX_VALUE;
        this.includeTests = includeTests;
    }

    /**
     * Discovers all {@code .java} files under {@code sourceRoot} (excluding
     * {@code module-info.java}), resolves cross-file calls, and returns a
     * merged call-sequence graph.
     */
    public Graph parse(Path sourceRoot) {
        List<Path> files = collectJavaSources(sourceRoot);
        if (files.isEmpty()) {
            log.warning("No .java source files found under: " + sourceRoot);
            return new Graph();
        }
        return parse(sourceRoot, files);
    }

    /**
     * Parses an explicit list of {@code files} with symbol resolution anchored
     * at {@code sourceRoot}.
     */
    public Graph parse(Path sourceRoot, List<Path> files) {
        ParsedSources sources = parseSources(sourceRoot, files);
        Graph graph = new Graph();
        for (ParsedFile parsed : sources.files()) {
            parseFile(parsed.cu(), graph, sources.projectClasses());
        }
        pruneToMaxDepth(graph);
        return graph;
    }

    /**
     * One stitched sequence graph per directory holding a {@code .java} file, each drawing the
     * methods declared directly in that directory, keyed by directory in sorted order.
     *
     * <p>Every file under {@code sourceRoot} is parsed once, with the symbol solver anchored at
     * {@code sourceRoot} itself, so a call from one package into another still resolves and the
     * callee appears as a participant. Running each package directory as its own root instead
     * drew every subpackage again in its parent's diagram and anchored the solver at the
     * package, so cross-package calls fell back to scope-based names (issue #232).
     */
    public Map<Path, Graph> parsePerDirectory(Path sourceRoot) {
        ParsedSources sources = parseSources(sourceRoot, collectJavaSources(sourceRoot));
        Map<Path, List<CompilationUnit>> byDirectory = new java.util.TreeMap<>();
        for (ParsedFile parsed : sources.files()) {
            Path dir = parsed.file().getParent();
            if (dir != null) {
                byDirectory.computeIfAbsent(dir, k -> new java.util.ArrayList<>()).add(parsed.cu());
            }
        }
        Map<Path, Graph> graphs = new java.util.LinkedHashMap<>();
        byDirectory.forEach((dir, cus) -> {
            Graph graph = new Graph();
            for (CompilationUnit cu : cus) {
                parseFile(cu, graph, sources.projectClasses());
            }
            pruneToMaxDepth(graph);
            graphs.put(dir, graph);
        });
        return graphs;
    }

    private record ParsedFile(Path file, CompilationUnit cu) {}

    private record ParsedSources(List<ParsedFile> files, Set<String> projectClasses) {}

    private ParsedSources parseSources(Path sourceRoot, List<Path> files) {
        CombinedTypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver(false));
        for (Path root : findSourceRoots(sourceRoot)) {
            typeSolver.add(new JavaParserTypeSolver(root));
        }

        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                .setSymbolResolver(new JavaSymbolSolver(typeSolver));

        JavaParser parser = new JavaParser(config);
        List<ParsedFile> parsed = new java.util.ArrayList<>();
        Set<String> projectClasses = new java.util.HashSet<>();

        for (Path file : files) {
            try {
                ParseResult<CompilationUnit> result = parser.parse(Files.readString(file));
                if (result.isSuccessful() && result.getResult().isPresent()) {
                    CompilationUnit cu = result.getResult().get();
                    parsed.add(new ParsedFile(file, cu));
                    projectClasses.addAll(ParserSupport.collectProjectClasses(cu));
                } else {
                    log.warning("Failed to parse " + file);
                }
            } catch (Exception e) {
                log.warning("Failed to parse " + file + ": " + e.getMessage());
            }
        }
        return new ParsedSources(parsed, projectClasses);
    }

    private void parseFile(CompilationUnit cu, Graph graph, Set<String> projectClasses) {
        try {

            ParserSupport.methodBearingTypes(cu).forEach(classDecl -> {
                String className = classDecl.getFullyQualifiedName().orElse(classDecl.getNameAsString());
                if (FilterMatcher.matchesAny(className, customExcludes)) {
                    return;
                }
                graph.addNodeIfAbsent(new Node(className, ParserSupport.nodeTypeOf(classDecl), className));

                // Direct methods only: findAll would reach into nested and anonymous classes and
                // draw their methods on this class's lifeline (#265). Calls made inside an
                // anonymous class or lambda still count, for the method that declares it.
                classDecl.getMethods().forEach(method -> {
                    String methodId = MethodIds.of(className, classDecl, method);
                    if (FilterMatcher.matchesAny(className + "." + method.getNameAsString(), customExcludes)
                            || FilterMatcher.matchesAny(methodId, customExcludes) || FilterMatcher.matchesAny(method.getNameAsString(), customExcludes)) {
                        return;
                    }
                    graph.addNodeIfAbsent(new Node(methodId, NodeType.METHOD, method.getNameAsString()));

                    int[] seq = { 0 };
                    method.accept(new VoidVisitorAdapter<Void>() {
                        @Override
                        public void visit(MethodCallExpr call, Void arg) {
                            String calleeId = resolveCallee(call, className, classDecl);
                            if (calleeId == null) {
                                super.visit(call, null);
                                return;
                            }

                            String calleeClass = calleeId;
                            int dotIdx = calleeId.lastIndexOf('.');
                            if (dotIdx != -1) {
                                calleeClass = calleeId.substring(0, dotIdx);
                            }
                            if (dotIdx == -1 || !projectClasses.contains(calleeClass)) {
                                super.visit(call, null);
                                return;
                            }

                            if (FilterMatcher.matchesAny(call.getNameAsString(), customExcludes) || FilterMatcher.matchesAny(calleeId, customExcludes)) {
                                super.visit(call, null);
                                return;
                            }
                            graph.addNodeIfAbsent(new Node(calleeId, NodeType.METHOD, call.getNameAsString()));

                            int n = ++seq[0];
                            Edge edge = new Edge(methodId + "-calls-" + calleeId + "-" + n,
                                    methodId, calleeId, EdgeType.CALLS);
                            edge.setLabel(String.valueOf(n));
                            graph.addEdge(edge);

                            super.visit(call, null);
                        }
                    }, null);                });
            });

        } catch (Exception e) {
            log.warning("Failed to process CU: " + e.getMessage());
        }
    }

    /**
     * Resolves a call to a {@code Class.method} node id, or {@code null} when the
     * callee cannot be attributed to a named type. Callers filter the result
     * against the project-class set, so returning a variable name, a raw
     * expression string, or a bare static-import name here would leak
     * non-project participants into the diagram — skip those instead.
     */
    private String resolveCallee(MethodCallExpr call, String ownerClass, TypeDeclaration<?> owner) {
        try {
            ResolvedMethodDeclaration resolved = call.resolve();
            return MethodIds.forResolved(resolved);
        } catch (Exception ignored) {
            // Symbol resolution unavailable for this call — fall back to scope-based naming
        }
        if (call.getScope().isPresent()) {
            Expression scope = call.getScope().get();
            if (scope.isNameExpr()) {
                return scope.asNameExpr().getNameAsString() + "." + call.getNameAsString();
            }
            if (scope.isThisExpr()) {
                String own = MethodIds.forLocalCall(ownerClass, owner, call);
                return own != null ? own : ownerClass + "." + call.getNameAsString();
            }
            return null; // constructor/cast/chained expression scopes — not attributable
        }
        // null for an unscoped non-local call (e.g. static import) — skip
        return MethodIds.forLocalCall(ownerClass, owner, call);
    }

    @AIKeepInSync(
        mirrors = "se.deversity.codekarta.render.SequenceDiagramRenderer#orderMessages",
        reason = "Both decide where a call tree starts: a METHOD no other method calls (self-calls do not count), then, for a cycle nothing outside calls, its first method in graph order. The tiers cannot share code (input and render may not reference each other), so they are two copies; if they disagree, --max-depth keeps a tree the renderer never draws, or the reverse (issue #229).",
        enforcedBy = "MultiFileSequenceParserTest.maxDepthKeepsTheCallTreeOfARecursiveEntryMethod / maxDepthCountsFromTheFirstMethodOfACycleWithNoOutsideCaller, and the SequenceDiagramRendererTest twins")
    private void pruneToMaxDepth(Graph graph) {
        if (maxDepth == Integer.MAX_VALUE) {
            return;
        }
        // 1. Call adjacency, and which methods some other method calls. A self-call does not
        //    count: a recursive method nothing else calls is still an entry point (issue #229).
        java.util.Map<String, java.util.List<String>> callees = new java.util.HashMap<>();
        java.util.Set<String> calledByOthers = new java.util.HashSet<>();
        for (Edge edge : graph.getEdges()) {
            if ("CALLS".equalsIgnoreCase(edge.getType())) {
                callees.computeIfAbsent(edge.getSourceId(), k -> new java.util.ArrayList<>()).add(edge.getTargetId());
                if (!edge.getSourceId().equals(edge.getTargetId())) {
                    calledByOthers.add(edge.getTargetId());
                }
            }
        }

        // 2. Minimum call depth of every reachable method, from depth-0 entry points: methods no
        //    other method calls, then, for a cycle nothing outside calls, its first method in
        //    graph order. Depths are computed in full before pruning, so a method beyond the
        //    limit is not mistaken for an unreached cycle and restarted at 0. Kept in step with
        //    SequenceDiagramRenderer.orderMessages, which must agree on where a tree starts.
        java.util.Map<String, Integer> depths = new java.util.HashMap<>();
        java.util.Deque<String> queue = new java.util.ArrayDeque<>();
        for (Node node : graph.getNodes()) {
            if (NodeType.METHOD.equals(node.getType()) && !calledByOthers.contains(node.getId())) {
                depths.put(node.getId(), 0);
                queue.add(node.getId());
            }
        }
        relaxDepths(queue, depths, callees);
        for (Node node : graph.getNodes()) {
            if (NodeType.METHOD.equals(node.getType()) && !depths.containsKey(node.getId())
                    && callees.containsKey(node.getId())) {
                depths.put(node.getId(), 0);
                queue.add(node.getId());
                relaxDepths(queue, depths, callees);
            }
        }

        // 3. Keep class nodes, exception nodes, and method nodes with depth <= maxDepth
        java.util.List<Node> keepNodes = new java.util.ArrayList<>();
        java.util.Set<String> keepNodeIds = new java.util.HashSet<>();
        for (Node node : graph.getNodes()) {
            // Keep class nodes and non-method nodes, or method nodes within maxDepth
            if (!NodeType.METHOD.equals(node.getType())
                    || depths.getOrDefault(node.getId(), Integer.MAX_VALUE) <= maxDepth) {
                keepNodes.add(node);
                keepNodeIds.add(node.getId());
            }
        }
        graph.setNodes(keepNodes);

        // 4. Keep only edges between kept nodes
        java.util.List<Edge> keepEdges = graph.getEdges().stream()
                .filter(e -> keepNodeIds.contains(e.getSourceId()) && keepNodeIds.contains(e.getTargetId()))
                .collect(java.util.stream.Collectors.toList());
        graph.setEdges(keepEdges);

        // 5. Keep groups (e.g. catch boundaries) containing only kept members
        for (Group group : graph.getGroups()) {
            java.util.List<String> keepMembers = group.getMemberIds().stream()
                    .filter(keepNodeIds::contains)
                    .collect(java.util.stream.Collectors.toList());
            group.setMemberIds(keepMembers);
        }
        graph.setGroups(graph.getGroups().stream()
                .filter(g -> !g.getMemberIds().isEmpty())
                .collect(java.util.stream.Collectors.toList()));
    }

    /** Unit-weight shortest paths from what is queued, lowering a depth whenever a shorter path appears. */
    private static void relaxDepths(java.util.Deque<String> queue, java.util.Map<String, Integer> depths,
                                    java.util.Map<String, java.util.List<String>> callees) {
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int nextDepth = depths.get(current) + 1;
            for (String target : callees.getOrDefault(current, java.util.List.of())) {
                if (nextDepth < depths.getOrDefault(target, Integer.MAX_VALUE)) {
                    depths.put(target, nextDepth);
                    queue.add(target);
                }
            }
        }
    }

    private List<Path> collectJavaSources(Path root) {
        try {
            // SourceTree sorts: walk order is filesystem-defined, and it reaches the SVG.
            return SourceTree.javaFiles(root, includeTests).stream()
                    .filter(p -> !"module-info.java".equals(String.valueOf(p.getFileName())))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.warning("Cannot walk source root " + root + ": " + e.getMessage());
            return List.of();
        }
    }

    private List<Path> findSourceRoots(Path root) {
        List<Path> roots = new java.util.ArrayList<>();
        try {
            // Sorted: source-root order decides which module's classes are stitched first.
            roots.addAll(SourceTree.sourceRoots(root));
        } catch (IOException e) {
            log.warning("Cannot walk source root to find type solver roots: " + e.getMessage());
        }
        if (roots.isEmpty()) {
            roots.add(root);
        }
        return roots;
    }
}
