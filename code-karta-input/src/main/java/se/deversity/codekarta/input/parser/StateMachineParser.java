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
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.YieldStmt;
import com.github.javaparser.ast.type.Type;
import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.EdgeType;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Node;
import se.deversity.codekarta.core.model.NodeType;
import se.deversity.vibetags.annotations.AIArchitecture;
import se.deversity.vibetags.annotations.AIContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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

    /** Reads production sources only when given a project or repository root. */
    public StateMachineParser() {
        this(false);
    }

    /**
     * @param includeTests {@code true} to read test source sets too when the input is a
     *                     directory; see {@link SourceTree}
     */
    public StateMachineParser(boolean includeTests) {
        this.includeTests = includeTests;
    }

    /**
     * Two passes over the input: every enum first, then every file's transitions against all
     * of them. A per-file pass skipped the file holding the transitions whenever the enum lived
     * in a file of its own, which is the usual layout (issue #228).
     */
    public Graph parse(Path path) {
        Graph graph = new Graph();
        JavaParser parser = ParserSupport.newJava21Parser();
        try {
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
            States states = collectStates(parsed, graph);
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

    private record ParsedFile(Path file, CompilationUnit cu) {}

    /** The enums of the input: their constants are the states, their names the state types. */
    private static final class States {
        private final Map<String, Set<String>> enumsByConstant = new HashMap<>();
        private final Set<String> enumNames = new HashSet<>();

        void add(String enumName, String constant) {
            enumNames.add(enumName);
            enumsByConstant.computeIfAbsent(constant, k -> new HashSet<>()).add(enumName);
        }

        boolean isEmpty() {
            return enumsByConstant.isEmpty();
        }

        boolean contains(String constant) {
            return enumsByConstant.containsKey(constant);
        }

        /** Whether a declared type (as written, generics and all) is one of the input's enums. */
        boolean isStateType(Type type) {
            String raw = ClassDiagramParser.rawType(type.asString());
            return enumNames.contains(raw.substring(raw.lastIndexOf('.') + 1));
        }

        /**
         * Whether two constants can be states of one machine, i.e. some enum declares both. A
         * switch that maps one enum onto another ({@code case PAID: return Payment.CAPTURED})
         * is a conversion, not a state change.
         */
        boolean sameEnum(String a, String b) {
            Set<String> enumsOfA = enumsByConstant.getOrDefault(a, Set.of());
            return enumsByConstant.getOrDefault(b, Set.of()).stream().anyMatch(enumsOfA::contains);
        }
    }

    private void scanTransitions(ParsedFile file, States states, Graph graph) {
        try {
            AtomicInteger edgeSeq = new AtomicInteger(graph.getEdges().size());
            file.cu().findAll(MethodDeclaration.class).forEach(method -> {
                Set<String> stateVars = typedStateVariables(method, states);
                parseSwitchTransitions(method, states, stateVars, graph, edgeSeq);
                parseExplicitTransitionCalls(method, states, graph, edgeSeq);
                parseLinearStateAssignments(method, states, stateVars, graph, edgeSeq);
            });
        } catch (Exception e) {
            log.warning("Failed to parse " + file.file() + " as state machine: " + e.getMessage());
        }
    }

    private States collectStates(List<ParsedFile> parsed, Graph graph) {
        States states = new States();
        for (ParsedFile file : parsed) {
            file.cu().findAll(EnumDeclaration.class).forEach(enumDecl ->
                    enumDecl.getEntries().forEach(entry -> {
                        String state = entry.getNameAsString();
                        states.add(enumDecl.getNameAsString(), state);
                        graph.addNodeIfAbsent(new Node(state, NodeType.STATE, state));
                    }));
        }
        return states;
    }

    /**
     * Lower-cased names of the variables {@code method} can assign a state to, decided by
     * declared type: fields of the enclosing type, locals and parameters whose type is one of the
     * input's enums. A field called {@code status} or {@code phase} is as much a state variable
     * as one called {@code state}; the name rule in {@link #looksLikeStateTarget} stays only as
     * the fallback for {@code var} and other untyped cases.
     */
    private Set<String> typedStateVariables(MethodDeclaration method, States states) {
        Set<String> names = new LinkedHashSet<>();
        method.findAncestor(TypeDeclaration.class).ifPresent(type ->
                type.findAll(FieldDeclaration.class).forEach(field -> field.getVariables().forEach(v -> {
                    if (states.isStateType(v.getType())) {
                        names.add(v.getNameAsString().toLowerCase(Locale.ROOT));
                    }
                })));
        method.findAll(VariableDeclarator.class).forEach(v -> {
            if (states.isStateType(v.getType())) {
                names.add(v.getNameAsString().toLowerCase(Locale.ROOT));
            }
        });
        method.getParameters().forEach(p -> {
            if (states.isStateType(p.getType())) {
                names.add(p.getNameAsString().toLowerCase(Locale.ROOT));
            }
        });
        return names;
    }

    private void parseSwitchTransitions(MethodDeclaration method, States states, Set<String> stateVars,
                                        Graph graph, AtomicInteger edgeSeq) {
        method.findAll(SwitchEntry.class).forEach(entry -> {
            Set<String> sources = new LinkedHashSet<>();
            entry.getLabels().forEach(label -> stateName(label, states).ifPresent(sources::add));
            if (sources.isEmpty()) {
                return;
            }

            Set<String> targets = new LinkedHashSet<>();
            entry.findAll(AssignExpr.class).forEach(assign -> {
                if (looksLikeStateTarget(assign.getTarget(), stateVars)) {
                    stateName(assign.getValue(), states).ifPresent(targets::add);
                }
            });
            entry.findAll(ReturnStmt.class).forEach(ret ->
                    ret.getExpression().flatMap(expr -> stateName(expr, states)).ifPresent(targets::add));
            entry.findAll(YieldStmt.class).forEach(yield ->
                    stateName(yield.getExpression(), states).ifPresent(targets::add));

            for (String source : sources) {
                for (String target : targets) {
                    addTransition(graph, states, source, target, method.getNameAsString(), edgeSeq);
                }
            }
        });
    }

    private void parseExplicitTransitionCalls(MethodDeclaration method, States states,
                                              Graph graph, AtomicInteger edgeSeq) {
        method.findAll(MethodCallExpr.class).forEach(call -> {
            if (!"transition".equalsIgnoreCase(call.getNameAsString()) || call.getArguments().size() < 2) {
                return;
            }
            Optional<String> source = stateName(call.getArgument(0), states);
            Optional<String> target = stateName(call.getArgument(1), states);
            if (source.isEmpty() || target.isEmpty()) {
                return;
            }
            String label = call.getArguments().size() >= 3 && call.getArgument(2).isStringLiteralExpr()
                    ? call.getArgument(2).asStringLiteralExpr().asString()
                    : method.getNameAsString();
            addTransition(graph, states, source.get(), target.get(), label, edgeSeq);
        });
    }

    private void parseLinearStateAssignments(MethodDeclaration method, States states, Set<String> typedStateVars,
                                             Graph graph, AtomicInteger edgeSeq) {
        // State variables: typed ones, plus any variable initialised to a known state value
        Set<String> stateVarNames = new LinkedHashSet<>(typedStateVars);
        method.findAll(VariableDeclarator.class).forEach(v -> {
            if (v.getInitializer().flatMap(expr -> stateName(expr, states)).isPresent()) {
                stateVarNames.add(v.getNameAsString().toLowerCase(Locale.ROOT));
            }
        });
        String[] previous = {initialStateName(method, states, typedStateVars)
                .or(() -> localVarInitialState(method, states, stateVarNames))
                .orElse(null)};
        method.findAll(AssignExpr.class).forEach(assign -> {
            if (!looksLikeStateTarget(assign.getTarget(), stateVarNames)) {
                return;
            }
            Optional<String> target = stateName(assign.getValue(), states);
            if (target.isEmpty()) {
                return;
            }
            if (previous[0] != null && !previous[0].equals(target.get())) {
                addTransition(graph, states, previous[0], target.get(), method.getNameAsString(), edgeSeq);
            }
            previous[0] = target.get();
        });
    }

    private Optional<String> initialStateName(MethodDeclaration method, States states, Set<String> typedStateVars) {
        return method.findAncestor(TypeDeclaration.class)
                .flatMap(type -> type.findAll(FieldDeclaration.class).stream()
                        .flatMap(field -> field.getVariables().stream())
                        .filter(var -> {
                            String nameLower = var.getNameAsString().toLowerCase(Locale.ROOT);
                            return nameLower.contains("state") || typedStateVars.contains(nameLower);
                        })
                        .flatMap(var -> var.getInitializer().flatMap(expr -> stateName(expr, states)).stream())
                        .findFirst());
    }

    private Optional<String> localVarInitialState(MethodDeclaration method, States states,
                                                   Set<String> stateVarNames) {
        return method.findAll(VariableDeclarator.class).stream()
                .filter(v -> {
                    String nameLower = v.getNameAsString().toLowerCase(Locale.ROOT);
                    return nameLower.contains("state") || stateVarNames.contains(nameLower);
                })
                .flatMap(v -> v.getInitializer().flatMap(expr -> stateName(expr, states)).stream())
                .findFirst();
    }

    private Optional<String> stateName(Expression expr, States states) {
        String candidate = null;
        if (expr.isNameExpr()) {
            candidate = expr.asNameExpr().getNameAsString();
        } else if (expr.isFieldAccessExpr()) {
            candidate = expr.asFieldAccessExpr().getNameAsString();
        } else if (expr.isStringLiteralExpr()) {
            candidate = expr.asStringLiteralExpr().asString();
        }
        return candidate != null && states.contains(candidate) ? Optional.of(candidate) : Optional.empty();
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

    private void addTransition(Graph graph, States states, String source, String target,
                               String label, AtomicInteger edgeSeq) {
        if (!states.sameEnum(source, target)) {
            return;
        }
        boolean exists = graph.getEdges().stream()
                .anyMatch(edge -> EdgeType.TRANSITION.equals(edge.getType())
                        && source.equals(edge.getSourceId())
                        && target.equals(edge.getTargetId())
                        && label.equals(edge.getLabel()));
        if (exists) {
            return;
        }
        graph.addNodeIfAbsent(new Node(source, NodeType.STATE, source));
        graph.addNodeIfAbsent(new Node(target, NodeType.STATE, target));
        Edge edge = new Edge(source + "-transition-" + target + "-" + edgeSeq.incrementAndGet(),
                source, target, EdgeType.TRANSITION);
        edge.setLabel(label);
        graph.addEdge(edge);
    }
}
