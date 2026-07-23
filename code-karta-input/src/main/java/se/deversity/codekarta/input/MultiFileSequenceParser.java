package se.deversity.codekarta.input;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
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
import se.deversity.codekarta.input.parser.ParserSupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import se.deversity.vibetags.annotations.AIArchitecture;
import se.deversity.vibetags.annotations.AIContext;

/**
 * Parses multiple Java source files and stitches their call graphs into a
 * single unified {@link Graph}.
 *
 * <p>
 * Cross-file method calls are resolved via JavaParser's
 * {@link JavaSymbolSolver}: when a callee's declaring type can be determined,
 * the callee node id uses the qualified form {@code ClassName.methodName} so
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
    focus = "Cross-file call resolution via JavaSymbolSolver: resolved callees get 'ClassName.methodName' ids so nodes from different source files are automatically linked. Unresolvable calls fall back to scope-based naming without crashing.",
    avoids = "Adding exception-flow parsing here — catch-boundary groups and EXCEPTION_PROPAGATION edges belong to ExceptionFlowParser on individual files, not to the multi-file stitching pass."
)
@AIArchitecture(belongsTo = "input", cannotReference = {"layout", "render", "cli"})
public class MultiFileSequenceParser {

    private static final Logger log = Logger.getLogger(MultiFileSequenceParser.class.getName());

    private final Set<String> customExcludes;
    private final int maxDepth;

    public MultiFileSequenceParser() {
        this(java.util.Collections.emptySet(), Integer.MAX_VALUE);
    }

    public MultiFileSequenceParser(Set<String> customExcludes) {
        this(customExcludes, Integer.MAX_VALUE);
    }

    public MultiFileSequenceParser(Set<String> customExcludes, int maxDepth) {
        this.customExcludes = ParserSupport.normalizeExcludes(customExcludes);
        this.maxDepth = maxDepth > 0 ? maxDepth : Integer.MAX_VALUE;
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
        Graph graph = new Graph();

        CombinedTypeSolver typeSolver = new CombinedTypeSolver(new ReflectionTypeSolver(false));
        for (Path root : findSourceRoots(sourceRoot)) {
            typeSolver.add(new JavaParserTypeSolver(root));
        }

        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                .setSymbolResolver(new JavaSymbolSolver(typeSolver));

        JavaParser parser = new JavaParser(config);
        for (Path file : files) {
            parseFile(parser, file, graph);
        }

        pruneToMaxDepth(graph);
        return graph;
    }

    private void parseFile(JavaParser parser, Path file, Graph graph) {
        try {
            ParseResult<CompilationUnit> result = parser.parse(Files.readString(file));
            if (!result.isSuccessful() || result.getResult().isEmpty()) {
                log.warning("Failed to parse " + file);
                return;
            }
            CompilationUnit cu = result.getResult().get();

            cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
                String className = classDecl.getFullyQualifiedName().orElse(classDecl.getNameAsString());
                if (FilterMatcher.matchesAny(className, customExcludes)) {
                    return;
                }
                graph.addNodeIfAbsent(new Node(className, NodeType.CLASS, className));

                Set<String> localMethods = classDecl.findAll(MethodDeclaration.class).stream()
                        .map(MethodDeclaration::getNameAsString)
                        .collect(Collectors.toSet());

                classDecl.findAll(MethodDeclaration.class).forEach(method -> {
                    String methodId = className + "." + method.getNameAsString();
                    if (FilterMatcher.matchesAny(methodId, customExcludes) || FilterMatcher.matchesAny(method.getNameAsString(), customExcludes)) {
                        return;
                    }
                    graph.addNodeIfAbsent(new Node(methodId, NodeType.METHOD, method.getNameAsString()));

                    int[] seq = { 0 };
                    method.accept(new VoidVisitorAdapter<Void>() {
                        @Override
                        public void visit(MethodCallExpr call, Void arg) {
                            String calleeId = resolveCallee(call, className, localMethods);
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
                    }, null);

                    // Catch-boundary groups (structural context, no exception edges)
                    ParserSupport.addCatchBoundaryGroups(graph, method, methodId,
                            call -> resolveCallee(call, className, localMethods));
                });
            });

        } catch (Exception e) {
            log.warning("Failed to parse " + file + ": " + e.getMessage());
        }
    }

    private String resolveCallee(MethodCallExpr call, String ownerClass, Set<String> localMethods) {
        try {
            ResolvedMethodDeclaration resolved = call.resolve();
            String declaringType = resolved.declaringType().getQualifiedName();
            return declaringType + "." + resolved.getName();
        } catch (Exception ignored) {
            // Symbol resolution unavailable for this call — fall back to scope-based naming
        }
        return call.getScope()
                .map(s -> s + "." + call.getNameAsString())
                .orElse(localMethods.contains(call.getNameAsString())
                        ? ownerClass + "." + call.getNameAsString()
                        : call.getNameAsString());
    }

    private void pruneToMaxDepth(Graph graph) {
        if (maxDepth == Integer.MAX_VALUE) {
            return;
        }
        // 1. Identify all nodes that have incoming CALLS edges
        java.util.Set<String> hasIncoming = new java.util.HashSet<>();
        for (Edge edge : graph.getEdges()) {
            if ("CALLS".equalsIgnoreCase(edge.getType())) {
                hasIncoming.add(edge.getTargetId());
            }
        }

        // 2. Entry points are METHOD nodes with no incoming CALLS edges
        java.util.Queue<String> queue = new java.util.ArrayDeque<>();
        java.util.Map<String, Integer> depths = new java.util.HashMap<>();
        for (Node node : graph.getNodes()) {
            if (NodeType.METHOD.equals(node.getType()) && !hasIncoming.contains(node.getId())) {
                depths.put(node.getId(), 0);
                queue.add(node.getId());
            }
        }

        // BFS to find the minimum depth of each node
        java.util.Set<String> visited = new java.util.HashSet<>();
        while (!queue.isEmpty()) {
            String current = queue.poll();
            int currentDepth = depths.get(current);
            if (!visited.add(current)) {
                continue;
            }
            if (currentDepth >= maxDepth) {
                continue;
            }
            for (Edge edge : graph.getEdges()) {
                if ("CALLS".equalsIgnoreCase(edge.getType()) && current.equals(edge.getSourceId())) {
                    String target = edge.getTargetId();
                    int nextDepth = currentDepth + 1;
                    if (nextDepth < depths.getOrDefault(target, Integer.MAX_VALUE)) {
                        depths.put(target, nextDepth);
                        queue.add(target);
                    }
                }
            }
        }

        // 3. Keep class nodes, exception nodes, and method nodes with depth <= maxDepth
        java.util.List<Node> keepNodes = new java.util.ArrayList<>();
        java.util.Set<String> keepNodeIds = new java.util.HashSet<>();
        for (Node node : graph.getNodes()) {
            // Keep class nodes and non-method nodes, or method nodes within maxDepth
            if (!NodeType.METHOD.equals(node.getType()) || depths.containsKey(node.getId())) {
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

    private List<Path> collectJavaSources(Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !"module-info.java".equals(String.valueOf(p.getFileName())))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.warning("Cannot walk source root " + root + ": " + e.getMessage());
            return List.of();
        }
    }

    private List<Path> findSourceRoots(Path root) {
        List<Path> roots = new java.util.ArrayList<>();
        try (Stream<Path> stream = Files.walk(root)) {
            stream.filter(Files::isDirectory)
                  .filter(p -> {
                      Path fn = p.getFileName();
                      return fn != null && "java".equals(fn.toString());
                  })
                  .filter(p -> {
                      Path parent = p.getParent();
                      if (parent == null) return false;
                      Path pfn = parent.getFileName();
                      return pfn != null && ("main".equals(pfn.toString()) || "test".equals(pfn.toString()));
                  })
                  .forEach(roots::add);
        } catch (IOException e) {
            log.warning("Cannot walk source root to find type solver roots: " + e.getMessage());
        }
        if (roots.isEmpty()) {
            roots.add(root);
        }
        return roots;
    }
}
