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
import se.deversity.vibetags.annotations.AIArchitecture;
import se.deversity.vibetags.annotations.AIContext;
import se.deversity.vibetags.annotations.AILoadBearing;

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
@AIContext(
    focus = "Only the mechanical parse skeleton belongs here — JavaParser configuration, exclude normalisation, and the per-class iteration every single-file parser repeats. Helpers stay caller-agnostic: they take the Graph and a callback rather than deciding what nodes or edges mean.",
    avoids = "Absorbing diagram semantics from the callers. A helper that knows about CALLS labels, EXCEPTION_PROPAGATION edges, or try/catch Groups belongs in the parser that owns that diagram type, not in the shared skeleton."
)
@AIArchitecture(belongsTo = "input", cannotReference = {"layout", "render", "cli"})
@AILoadBearing(
    invariant = "parseJava21 propagates its failures instead of swallowing them; the try/catch that turns a failure into a partial graph stays in each calling parser.",
    breaksIf = "Wrapping the fault tolerance in here looks like deduplication and silently changes the contract: every caller would inherit one shared recovery policy, and a parser that needs to log its own diagnostic or return a differently-shaped partial graph could no longer do so. The repeated try/catch in the callers is the fault-tolerance rule being stated once per parser, not copy-paste.",
    suppressAudit = true)
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
     * Shared per-class prologue of the single-file parsers: iterates the
     * compilation unit's class/interface declarations, skips excluded names,
     * adds a CLASS node for each accepted declaration, and hands the
     * declaration plus its name to {@code body}.
     */
    public static void forEachIncludedClass(CompilationUnit cu, Set<String> customExcludes,
            Graph graph, java.util.function.BiConsumer<ClassOrInterfaceDeclaration, String> body) {
        cu.findAll(ClassOrInterfaceDeclaration.class).forEach(classDecl -> {
            String className = classDecl.getNameAsString();
            if (FilterMatcher.matchesAny(className, customExcludes)) {
                return;
            }
            graph.addNodeIfAbsent(new Node(className, NodeType.CLASS, className));
            body.accept(classDecl, className);
        });
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
                // Only reference nodes the calling parser already accepted — creating
                // nodes here would bypass the parser's project/stdlib filters and leak
                // JDK types or raw variable names into the diagram as participants.
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

    public static java.util.Set<String> collectProjectClasses(CompilationUnit cu) {
        java.util.Set<String> projectClasses = new java.util.HashSet<>();
        cu.findAll(com.github.javaparser.ast.body.ClassOrInterfaceDeclaration.class).forEach(cd -> {
            projectClasses.add(cd.getFullyQualifiedName().orElse(cd.getNameAsString()));
            projectClasses.add(cd.getNameAsString());
        });
        return projectClasses;
    }
}
