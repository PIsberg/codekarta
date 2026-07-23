package se.deversity.codekarta.input.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.TryStmt;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Group;
import se.deversity.codekarta.core.model.Node;
import se.deversity.codekarta.core.model.NodeType;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;

/**
 * Shared building blocks for the single-file parsers. Extracted so
 * {@code CallSequenceParser}, {@code ExceptionFlowParser} and
 * {@code MultiFileSequenceParser} don't each carry their own copy of the
 * parse skeleton (fault-tolerance idioms stay in the individual parsers).
 */
public final class ParserSupport {

    private static final JavaParser PARSER = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));

    private ParserSupport() {
    }

    /** Null-safe normalisation used by every parser constructor taking excludes. */
    public static Set<String> normalizeExcludes(Set<String> customExcludes) {
        return customExcludes != null ? customExcludes : Collections.emptySet();
    }

    /**
     * Parses {@code sourceFile} at the Java 21 language level.
     * Callers keep their own try/catch — the fault-tolerance contract
     * (log a warning, return a partial graph) belongs to the parser;
     * unparseable sources surface as {@link java.util.NoSuchElementException}.
     */
    public static CompilationUnit parseJava21(Path sourceFile) throws java.io.IOException {
        return PARSER.parse(Files.readString(sourceFile)).getResult().orElseThrow();
    }

    /**
     * Method name → raw return type (generics stripped), used to resolve
     * chained local calls such as {@code resolveLayout(x).layout(g)}.
     */
    public static Map<String, String> returnTypesOf(ClassOrInterfaceDeclaration classDecl) {
        Map<String, String> returnTypes = new HashMap<>();
        classDecl.findAll(MethodDeclaration.class).forEach(m -> {
            String rt = m.getType().asString();
            int lt = rt.indexOf('<');
            returnTypes.put(m.getNameAsString(), lt > 0 ? rt.substring(0, lt).trim() : rt.trim());
        });
        return returnTypes;
    }

    /**
     * Adds one catch-boundary {@link Group} per try-statement in {@code method},
     * with a member per call inside the try block. {@code calleeResolver} maps a
     * call to its node id, or returns {@code null} to skip the call.
     */
    public static void addCatchBoundaryGroups(Graph graph, MethodDeclaration method, String methodId,
            Function<MethodCallExpr, String> calleeResolver) {
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
                String callee = calleeResolver.apply(call);
                if (callee == null) {
                    return;
                }
                graph.addNodeIfAbsent(new Node(callee, NodeType.METHOD, call.getNameAsString()));
                group.addMember(callee);
            });

            if (!group.getMemberIds().isEmpty()) {
                graph.addGroup(group);
            }
        });
    }

    public static java.util.Set<String> collectProjectClasses(CompilationUnit cu) {
        java.util.Set<String> projectClasses = new java.util.HashSet<>();
        cu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class).forEach(cd -> {
            projectClasses.add(cd.getFullyQualifiedName().orElse(cd.getNameAsString()));
            projectClasses.add(cd.getNameAsString());
        });
        return projectClasses;
    }
}
