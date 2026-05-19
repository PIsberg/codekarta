package com.karta.input;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.TryStmt;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import com.github.javaparser.resolution.declarations.ResolvedMethodDeclaration;
import com.github.javaparser.symbolsolver.JavaSymbolSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.CombinedTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.JavaParserTypeSolver;
import com.github.javaparser.symbolsolver.resolution.typesolvers.ReflectionTypeSolver;
import com.karta.core.model.Edge;
import com.karta.core.model.EdgeType;
import com.karta.core.model.Graph;
import com.karta.core.model.Group;
import com.karta.core.model.Node;
import com.karta.core.model.NodeType;

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

        CombinedTypeSolver typeSolver = new CombinedTypeSolver(
                new ReflectionTypeSolver(false),
                new JavaParserTypeSolver(sourceRoot));
        ParserConfiguration config = new ParserConfiguration()
                .setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21)
                .setSymbolResolver(new JavaSymbolSolver(typeSolver));

        JavaParser parser = new JavaParser(config);
        for (Path file : files) {
            parseFile(parser, file, graph);
        }

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
                String className = classDecl.getNameAsString();
                graph.addNodeIfAbsent(new Node(className, NodeType.CLASS, className));

                Set<String> localMethods = classDecl.findAll(MethodDeclaration.class).stream()
                        .map(MethodDeclaration::getNameAsString)
                        .collect(Collectors.toSet());

                classDecl.findAll(MethodDeclaration.class).forEach(method -> {
                    String methodId = className + "." + method.getNameAsString();
                    graph.addNodeIfAbsent(new Node(methodId, NodeType.METHOD, method.getNameAsString()));

                    int[] seq = { 0 };
                    method.accept(new VoidVisitorAdapter<Void>() {
                        @Override
                        public void visit(MethodCallExpr call, Void arg) {
                            String calleeId = resolveCallee(call, className, localMethods);
                            graph.addNodeIfAbsent(new Node(calleeId, NodeType.METHOD, call.getNameAsString()));

                            int n = ++seq[0];
                            Edge edge = new Edge(methodId + "-calls-" + calleeId + "-" + n,
                                    methodId, calleeId, EdgeType.CALLS);
                            edge.setLabel(String.valueOf(n));
                            graph.addEdge(edge);

                            super.visit(call, arg);
                        }
                    }, null);

                    // Catch-boundary groups (structural context, no exception edges)
                    int[] tryIdx = { 0 };
                    method.findAll(TryStmt.class).forEach(tryStmt -> {
                        String catchTypes = tryStmt.getCatchClauses().stream()
                                .map(c -> c.getParameter().getType().asString())
                                .reduce((a, b) -> a + ", " + b)
                                .orElse("Exception");

                        Group group = new Group(
                                "catch-boundary-" + methodId + "-" + tryIdx[0]++,
                                "catch(" + catchTypes + ")");

                        tryStmt.getTryBlock().findAll(MethodCallExpr.class).forEach(call -> {
                            String callee = resolveCallee(call, className, localMethods);
                            graph.addNodeIfAbsent(new Node(callee, NodeType.METHOD, call.getNameAsString()));
                            group.addMember(callee);
                        });

                        if (!group.getMemberIds().isEmpty()) {
                            graph.addGroup(group);
                        }
                    });
                });
            });

        } catch (Exception e) {
            log.warning("Failed to parse " + file + ": " + e.getMessage());
        }
    }

    private String resolveCallee(MethodCallExpr call, String ownerClass, Set<String> localMethods) {
        try {
            ResolvedMethodDeclaration resolved = call.resolve();
            String declaringType = resolved.declaringType().getClassName();
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

    private List<Path> collectJavaSources(Path root) {
        try (Stream<Path> stream = Files.walk(root)) {
            return stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .filter(p -> !"module-info.java".equals(p.getFileName().toString()))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            log.warning("Cannot walk source root " + root + ": " + e.getMessage());
            return List.of();
        }
    }
}
