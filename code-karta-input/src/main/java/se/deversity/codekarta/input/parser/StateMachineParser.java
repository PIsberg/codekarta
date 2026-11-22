package se.deversity.codekarta.input.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.expr.SwitchExpr;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.SwitchStmt;
import com.github.javaparser.ast.stmt.YieldStmt;
import com.github.javaparser.ast.type.Type;
import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.EdgeType;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Node;
import se.deversity.codekarta.core.model.NodeType;
import org.jspecify.annotations.Nullable;
import se.deversity.vibetags.annotations.AIArchitecture;
import se.deversity.vibetags.annotations.AIContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;

@AIContext(
    focus = "Extracts enum-backed state machines. Enum constants become STATE nodes. Switch cases over state values and explicit transition(from,to[,event]) calls become TRANSITION edges.",
    avoids = "Treating arbitrary enum usage as a state machine without a transition source/target. Parser must fail softly and return partial graphs."
)
@AIArchitecture(belongsTo = "input", cannotReference = {"layout", "render", "cli"})
public class StateMachineParser {

    private static final Logger log = Logger.getLogger(StateMachineParser.class.getName());

    private final boolean includeTests;
    private final Set<String> customExcludes;

    /** Reads production sources only when given a project or repository root. */
    public StateMachineParser() {
        this(false);
    }

    /**
     * @param includeTests {@code true} to read test source sets too when the input is a
     *                     directory; see {@link SourceTree}
     */
    public StateMachineParser(boolean includeTests) {
        this(includeTests, java.util.Collections.emptySet());
    }

    /**
     * @param customExcludes {@code --exclude} patterns; an enum whose simple or qualified name
     *                       matches contributes no states, and a state whose id matches is dropped
     */
    public StateMachineParser(boolean includeTests, Set<String> customExcludes) {
        this.includeTests = includeTests;
        this.customExcludes = ParserSupport.normalizeExcludes(customExcludes);
    }

    /**
     * Two passes over the input: every enum first, then every file's transitions against all
     * of them. A per-file pass skipped the file holding the transitions whenever the enum lived
     * in a file of its own, which is the usual layout (issue #228).
     */
    public Graph parse(Path path) {
        Graph graph = new Graph();
        try {
            List<ParsedFile> parsed = parseFiles(path);
            States states = collectStates(parsed);
            addStateNodes(parsed, graph);
            if (states.isEmpty()) {
                return graph;
            }
            for (ParsedFile file : parsed) {
                scanTransitions(file, states, graph);
            }
        } catch (Exception e) {
            log.warning("Failed to parse state machine input " + path + ": " + e.getMessage());
        }
        return graph;
    }

    /**
     * One state-machine graph per directory holding a {@code .java} file, keyed by directory in
     * sorted order. Each holds the states of the enums declared in that directory and the
     * transitions found in its files, with the states they touch.
     *
     * <p>Every enum of the whole tree is read first, as {@link #parse} does, so a transition whose
     * enum lives in another package still resolves. Rendering each directory recursively instead
     * drew every subpackage's machines again in each parent package (issue #246).
     */
    public Map<Path, Graph> parsePerDirectory(Path root) {
        Map<Path, Graph> graphs = new java.util.LinkedHashMap<>();
        try {
            List<ParsedFile> parsed = parseFiles(root);
            States states = collectStates(parsed);
            Map<Path, List<ParsedFile>> byDirectory = new java.util.TreeMap<>();
            for (ParsedFile file : parsed) {
                Path dir = file.file().getParent();
                if (dir != null) {
                    byDirectory.computeIfAbsent(dir, k -> new ArrayList<>()).add(file);
                }
            }
            byDirectory.forEach((dir, files) -> {
                Graph graph = new Graph();
                addStateNodes(files, graph);
                if (!states.isEmpty()) {
                    files.forEach(file -> scanTransitions(file, states, graph));
                }
                graphs.put(dir, graph);
            });
        } catch (Exception e) {
            log.warning("Failed to parse state machine input " + root + ": " + e.getMessage());
        }
        return graphs;
    }

    private List<ParsedFile> parseFiles(Path path) throws java.io.IOException {
        JavaParser parser = ParserSupport.newJava21Parser();
        List<Path> files = Files.isDirectory(path)
                ? SourceTree.javaFiles(path, includeTests).stream()
                        .filter(p -> !"module-info.java".equals(String.valueOf(p.getFileName())))
                        .toList()
                : List.of(path);
        List<ParsedFile> parsed = new ArrayList<>();
        for (Path file : files) {
            try {
                parsed.add(new ParsedFile(file, parser.parse(Files.readString(file)).getResult().orElseThrow()));
            } catch (Exception e) {
                log.warning("Failed to parse " + file + " as state machine: " + e.getMessage());
            }
        }
        return parsed;
    }

    private record ParsedFile(Path file, CompilationUnit cu) {}

    /**
     * The enums of the input. A state's node id is {@code <enum>.<constant>}, where the enum is
     * named relative to its package ({@code OrderStatus}, {@code Shipment.State}); the label is
     * the constant. Keyed by bare constant names, two enums that share one ({@code
     * OrderStatus.ACTIVE}, {@code Mode.ACTIVE}) merged into one node, joining two machines into
     * one component (issue #239).
     */
    private static final class States {
        private final Map<String, Set<String>> enumsByConstant = new HashMap<>();
        private final Map<String, Set<String>> enumsBySimpleName = new HashMap<>();

        void add(String enumKey, String simpleName, String constant) {
            enumsBySimpleName.computeIfAbsent(simpleName, k -> new LinkedHashSet<>()).add(enumKey);
            enumsByConstant.computeIfAbsent(constant, k -> new LinkedHashSet<>()).add(enumKey);
        }

        boolean isEmpty() {
            return enumsByConstant.isEmpty();
        }

        Set<String> declaring(String constant) {
            return enumsByConstant.getOrDefault(constant, Set.of());
        }

        Set<String> named(String simpleName) {
            return enumsBySimpleName.getOrDefault(simpleName, Set.of());
        }

        /** The simple name of a declared type (as written, generics and all) if it is one of the enums. */
        Optional<String> stateTypeOf(Type type) {
            String raw = ClassDiagramParser.rawType(type.asString());
            String simple = raw.substring(raw.lastIndexOf('.') + 1);
            return enumsBySimpleName.containsKey(simple) ? Optional.of(simple) : Optional.empty();
        }
    }

    /** A reference to a state as written: the constant, and the enums it can belong to. */
    private record Ref(String constant, Set<String> enums) {}

    /**
     * What a method's state references are resolved against: lower-cased names of its state
     * variables mapped to their enum's simple name ({@code null} for one recognised by name or
     * initialiser only), and the enums declared in its own compilation unit.
     */
    private record Context(Map<String, String> stateVars, Set<String> localEnums) {

        Set<String> names() {
            return stateVars.keySet();
        }

        /** The enum type of the variable {@code expr} names, if it is a typed state variable. */
        @Nullable String typeOf(Expression expr) {
            String name = expr.isNameExpr() ? expr.asNameExpr().getNameAsString()
                    : expr.isFieldAccessExpr() ? expr.asFieldAccessExpr().getNameAsString()
                    : null;
            return name == null ? null : stateVars.get(name.toLowerCase(Locale.ROOT));
        }
    }

    private void scanTransitions(ParsedFile file, States states, Graph graph) {
        try {
            Set<String> localEnums = new LinkedHashSet<>();
            file.cu().findAll(EnumDeclaration.class).forEach(e -> localEnums.add(enumKey(e)));
            AtomicInteger edgeSeq = new AtomicInteger(graph.getEdges().size());
            file.cu().findAll(MethodDeclaration.class).forEach(method -> {
                Context context = new Context(typedStateVariables(method, states), localEnums);
                parseSwitchTransitions(method, states, context, graph, edgeSeq);
                parseExplicitTransitionCalls(method, states, context, graph, edgeSeq);
                parseLinearStateAssignments(method, states, context, graph, edgeSeq);
            });
        } catch (Exception e) {
            log.warning("Failed to parse " + file.file() + " as state machine: " + e.getMessage());
        }
    }

    /** An enum's name relative to its package: {@code OrderStatus}, or nested, {@code Shipment.State}. */
    private static String enumKey(EnumDeclaration enumDecl) {
        StringBuilder key = new StringBuilder(enumDecl.getNameAsString());
        Optional<com.github.javaparser.ast.Node> parent = enumDecl.getParentNode();
        while (parent.isPresent()) {
            if (parent.get() instanceof TypeDeclaration<?> outer) {
                key.insert(0, outer.getNameAsString() + ".");
            }
            parent = parent.get().getParentNode();
        }
        return key.toString();
    }

    private States collectStates(List<ParsedFile> parsed) {
        States states = new States();
        for (ParsedFile file : parsed) {
            file.cu().findAll(EnumDeclaration.class).forEach(enumDecl -> {
                if (excluded(enumDecl)) {
                    return;
                }
                String key = enumKey(enumDecl);
                enumDecl.getEntries().forEach(entry ->
                        states.add(key, enumDecl.getNameAsString(), entry.getNameAsString()));
            });
        }
        return states;
    }

    /** A STATE node for every constant of every enum declared in {@code files}, in source order. */
    private void addStateNodes(List<ParsedFile> files, Graph graph) {
        for (ParsedFile file : files) {
            file.cu().findAll(EnumDeclaration.class).forEach(enumDecl -> {
                if (excluded(enumDecl)) {
                    return;
                }
                String key = enumKey(enumDecl);
                enumDecl.getEntries().forEach(entry -> {
                    String id = key + "." + entry.getNameAsString();
                    if (!FilterMatcher.matchesAny(id, customExcludes)) {
                        graph.addNodeIfAbsent(new Node(id, NodeType.STATE, entry.getNameAsString()));
                    }
                });
            });
        }
    }

    /** An enum matched by --exclude, by simple name, nested key or qualified name, is not a machine. */
    private boolean excluded(EnumDeclaration enumDecl) {
        return FilterMatcher.matchesAny(enumDecl.getNameAsString(), customExcludes)
                || FilterMatcher.matchesAny(enumKey(enumDecl), customExcludes)
                || enumDecl.getFullyQualifiedName()
                        .map(fqn -> FilterMatcher.matchesAny(fqn, customExcludes)).orElse(false);
    }

    /**
     * The variables {@code method} can assign a state to, decided by declared type: fields of the
     * enclosing type, locals and parameters whose type is one of the input's enums, each mapped
     * to that enum's simple name. A field called {@code status} or {@code phase} is as much a
     * state variable as one called {@code state}; the name rule in {@link #looksLikeStateTarget}
     * stays only as the fallback for {@code var} and other untyped cases.
     */
    private Map<String, String> typedStateVariables(MethodDeclaration method, States states) {
        Map<String, String> vars = new LinkedHashMap<>();
        method.findAncestor(TypeDeclaration.class).ifPresent(type ->
                type.findAll(FieldDeclaration.class).forEach(field -> field.getVariables().forEach(v ->
                        states.stateTypeOf(v.getType()).ifPresent(t ->
                                vars.put(v.getNameAsString().toLowerCase(Locale.ROOT), t)))));
        method.findAll(VariableDeclarator.class).forEach(v ->
                states.stateTypeOf(v.getType()).ifPresent(t ->
                        vars.put(v.getNameAsString().toLowerCase(Locale.ROOT), t)));
        method.getParameters().forEach(p ->
                states.stateTypeOf(p.getType()).ifPresent(t ->
                        vars.put(p.getNameAsString().toLowerCase(Locale.ROOT), t)));
        return vars;
    }

    private void parseSwitchTransitions(MethodDeclaration method, States states, Context context,
                                        Graph graph, AtomicInteger edgeSeq) {
        method.findAll(SwitchEntry.class).forEach(entry -> {
            List<Ref> sources = new ArrayList<>();
            entry.getLabels().forEach(label -> stateRef(label, states).ifPresent(sources::add));
            if (sources.isEmpty()) {
                return;
            }
            // switch (status): the selector's declared type says which enum a bare label is from
            String hint = switchSelector(entry).map(context::typeOf).orElse(null);

            List<Ref> targets = new ArrayList<>();
            entry.findAll(AssignExpr.class).forEach(assign -> {
                if (looksLikeStateTarget(assign.getTarget(), context.names())) {
                    stateRef(assign.getValue(), states).ifPresent(targets::add);
                }
            });
            entry.findAll(ReturnStmt.class).forEach(ret ->
                    ret.getExpression().ifPresent(expr -> resultRefs(expr, states, targets)));
            entry.findAll(YieldStmt.class).forEach(yield ->
                    resultRefs(yield.getExpression(), states, targets));
            // case NEW -> OrderStatus.PAID; in a switch expression: the rule's expression is the
            // value the switch produces. It is neither a return, a yield nor an assignment, so the
            // idiomatic Java 14+ transition table used to yield nothing at all (issue #269).
            if (entry.getType() == SwitchEntry.Type.EXPRESSION
                    && entry.getParentNode().filter(SwitchExpr.class::isInstance).isPresent()) {
                entry.getStatements().stream()
                        .filter(Statement::isExpressionStmt)
                        .forEach(stmt -> resultRefs(stmt.asExpressionStmt().getExpression(), states, targets));
            }

            for (Ref source : sources) {
                for (Ref target : targets) {
                    addTransition(graph, states, context, source, target, hint,
                            method.getNameAsString(), edgeSeq);
                }
            }
        });
    }

    private static Optional<Expression> switchSelector(SwitchEntry entry) {
        return entry.getParentNode().flatMap(parent ->
                parent instanceof SwitchStmt stmt ? Optional.of(stmt.getSelector())
                        : parent instanceof SwitchExpr expr ? Optional.of(expr.getSelector())
                        : Optional.empty());
    }

    private void parseExplicitTransitionCalls(MethodDeclaration method, States states, Context context,
                                              Graph graph, AtomicInteger edgeSeq) {
        method.findAll(MethodCallExpr.class).forEach(call -> {
            if (!"transition".equalsIgnoreCase(call.getNameAsString()) || call.getArguments().size() < 2) {
                return;
            }
            Optional<Ref> source = stateRef(call.getArgument(0), states);
            Optional<Ref> target = stateRef(call.getArgument(1), states);
            if (source.isEmpty() || target.isEmpty()) {
                return;
            }
            String label = call.getArguments().size() >= 3 && call.getArgument(2).isStringLiteralExpr()
                    ? call.getArgument(2).asStringLiteralExpr().asString()
                    : method.getNameAsString();
            addTransition(graph, states, context, source.get(), target.get(), null, label, edgeSeq);
        });
    }

    private void parseLinearStateAssignments(MethodDeclaration method, States states, Context context,
                                             Graph graph, AtomicInteger edgeSeq) {
        // State variables: typed ones, plus any variable initialised to a known state value
        Set<String> stateVarNames = new LinkedHashSet<>(context.names());
        method.findAll(VariableDeclarator.class).forEach(v -> {
            if (v.getInitializer().flatMap(expr -> stateRef(expr, states)).isPresent()) {
                stateVarNames.add(v.getNameAsString().toLowerCase(Locale.ROOT));
            }
        });
        Ref[] previous = {initialState(method, states, context.names())
                .or(() -> localVarInitialState(method, states, stateVarNames))
                .orElse(null)};
        method.findAll(AssignExpr.class).forEach(assign -> {
            if (!looksLikeStateTarget(assign.getTarget(), stateVarNames)) {
                return;
            }
            Optional<Ref> target = stateRef(assign.getValue(), states);
            if (target.isEmpty()) {
                return;
            }
            if (previous[0] != null && !previous[0].constant().equals(target.get().constant())) {
                addTransition(graph, states, context, previous[0], target.get(),
                        context.typeOf(assign.getTarget()), method.getNameAsString(), edgeSeq);
            }
            previous[0] = target.get();
        });
    }

    private Optional<Ref> initialState(MethodDeclaration method, States states, Set<String> typedStateVars) {
        return method.findAncestor(TypeDeclaration.class)
                .flatMap(type -> type.findAll(FieldDeclaration.class).stream()
                        .flatMap(field -> field.getVariables().stream())
                        .filter(var -> {
                            String nameLower = var.getNameAsString().toLowerCase(Locale.ROOT);
                            return nameLower.contains("state") || typedStateVars.contains(nameLower);
                        })
                        .flatMap(var -> var.getInitializer().flatMap(expr -> stateRef(expr, states)).stream())
                        .findFirst());
    }

    private Optional<Ref> localVarInitialState(MethodDeclaration method, States states,
                                               Set<String> stateVarNames) {
        return method.findAll(VariableDeclarator.class).stream()
                .filter(v -> {
                    String nameLower = v.getNameAsString().toLowerCase(Locale.ROOT);
                    return nameLower.contains("state") || stateVarNames.contains(nameLower);
                })
                .flatMap(v -> v.getInitializer().flatMap(expr -> stateRef(expr, states)).stream())
                .findFirst();
    }

    /**
     * A state reference: a bare constant ({@code PAID}, or {@code "PAID"} in the transition DSL),
     * or a qualified one ({@code OrderStatus.PAID}) whose qualifier narrows which enums it can
     * belong to. Empty when no enum of the input declares the constant.
     */
    /** The states {@code expr} can produce: itself, or both branches of a {@code ?:}, through parentheses. */
    private void resultRefs(Expression expr, States states, List<Ref> sink) {
        if (expr.isEnclosedExpr()) {
            resultRefs(expr.asEnclosedExpr().getInner(), states, sink);
        } else if (expr.isConditionalExpr()) {
            resultRefs(expr.asConditionalExpr().getThenExpr(), states, sink);
            resultRefs(expr.asConditionalExpr().getElseExpr(), states, sink);
        } else {
            stateRef(expr, states).ifPresent(sink::add);
        }
    }

    private Optional<Ref> stateRef(Expression expr, States states) {
        String constant = null;
        Set<String> enums = Set.of();
        if (expr.isNameExpr()) {
            constant = expr.asNameExpr().getNameAsString();
            enums = states.declaring(constant);
        } else if (expr.isFieldAccessExpr()) {
            constant = expr.asFieldAccessExpr().getNameAsString();
            String scope = expr.asFieldAccessExpr().getScope().toString();
            Set<String> qualified = new LinkedHashSet<>(states.declaring(constant));
            qualified.retainAll(states.named(scope.substring(scope.lastIndexOf('.') + 1)));
            enums = qualified.isEmpty() ? states.declaring(constant) : qualified;
        } else if (expr.isStringLiteralExpr()) {
            constant = expr.asStringLiteralExpr().asString();
            enums = states.declaring(constant);
        }
        return constant != null && !enums.isEmpty() ? Optional.of(new Ref(constant, enums)) : Optional.empty();
    }

    /**
     * Whether an assignment target is a state variable: one of {@code stateVarNames} (by its
     * simple name, so {@code this.status} counts as {@code status}), or, as the fallback for
     * untyped cases, anything whose text mentions "state".
     */
    private boolean looksLikeStateTarget(Expression expr, Set<String> stateVarNames) {
        String name = expr.isNameExpr() ? expr.asNameExpr().getNameAsString()
                : expr.isFieldAccessExpr() ? expr.asFieldAccessExpr().getNameAsString()
                : expr.toString();
        return stateVarNames.contains(name.toLowerCase(Locale.ROOT))
                || expr.toString().toLowerCase(Locale.ROOT).contains("state");
    }

    /**
     * Adds {@code source -> target} as a transition of the one enum both can belong to. Both ends
     * must be constants of a single enum: a switch that maps one enum onto another ({@code case
     * PAID: return Payment.CAPTURED}) is a conversion, not a state change. When several enums
     * declare both constants, the declared type of the switch selector or assigned variable
     * ({@code hintType}) decides, then an enum declared in the same file; if none does, nothing
     * is drawn rather than a guess.
     */
    private void addTransition(Graph graph, States states, Context context, Ref source, Ref target,
                               @Nullable String hintType, String label, AtomicInteger edgeSeq) {
        Set<String> common = new LinkedHashSet<>(source.enums());
        common.retainAll(target.enums());
        String machine = pick(common, hintType == null ? Set.of() : states.named(hintType));
        if (machine == null) {
            machine = pick(common, context.localEnums());
        }
        if (machine == null) {
            return;
        }
        String sourceId = machine + "." + source.constant();
        String targetId = machine + "." + target.constant();
        if (FilterMatcher.matchesAny(sourceId, customExcludes) || FilterMatcher.matchesAny(targetId, customExcludes)) {
            return;
        }
        boolean exists = graph.getEdges().stream()
                .anyMatch(edge -> EdgeType.TRANSITION.equals(edge.getType())
                        && sourceId.equals(edge.getSourceId())
                        && targetId.equals(edge.getTargetId())
                        && label.equals(edge.getLabel()));
        if (exists) {
            return;
        }
        graph.addNodeIfAbsent(new Node(sourceId, NodeType.STATE, source.constant()));
        graph.addNodeIfAbsent(new Node(targetId, NodeType.STATE, target.constant()));
        Edge edge = new Edge(sourceId + "-transition-" + targetId + "-" + edgeSeq.incrementAndGet(),
                sourceId, targetId, EdgeType.TRANSITION);
        edge.setLabel(label);
        graph.addEdge(edge);
    }

    /** The only candidate, else the only candidate also in {@code preferred}, else {@code null}. */
    private static @Nullable String pick(Set<String> candidates, Set<String> preferred) {
        if (candidates.size() == 1) {
            return candidates.iterator().next();
        }
        String only = null;
        for (String candidate : candidates) {
            if (preferred.contains(candidate)) {
                if (only != null) {
                    return null;
                }
                only = candidate;
            }
        }
        return only;
    }
}
