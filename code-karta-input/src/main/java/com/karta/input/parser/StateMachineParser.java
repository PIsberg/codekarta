package com.karta.input.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.AssignExpr;
import com.github.javaparser.ast.expr.Expression;
import com.github.javaparser.ast.expr.MethodCallExpr;
import com.github.javaparser.ast.stmt.ReturnStmt;
import com.github.javaparser.ast.stmt.SwitchEntry;
import com.github.javaparser.ast.stmt.YieldStmt;
import com.karta.core.model.Edge;
import com.karta.core.model.EdgeType;
import com.karta.core.model.Graph;
import com.karta.core.model.Node;
import com.karta.core.model.NodeType;
import se.deversity.vibetags.annotations.AIArchitecture;
import se.deversity.vibetags.annotations.AIContext;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashSet;
import java.util.Locale;
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
    private static final JavaParser PARSER = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));

    public Graph parse(Path path) {
        Graph graph = new Graph();
        try {
            if (Files.isDirectory(path)) {
                try (var stream = Files.walk(path)) {
                    stream.filter(p -> p.toString().endsWith(".java"))
                            .filter(p -> !"module-info.java".equals(p.getFileName().toString()))
                            .forEach(p -> parseFile(p, graph));
                }
            } else {
                parseFile(path, graph);
            }
        } catch (Exception e) {
            log.warning("Failed to parse state machine input " + path + ": " + e.getMessage());
        }
        return graph;
    }

    private void parseFile(Path file, Graph graph) {
        try {
            CompilationUnit cu = PARSER.parse(Files.readString(file)).getResult().orElseThrow();
            Set<String> states = collectStates(cu, graph);
            if (states.isEmpty()) {
                return;
            }
            AtomicInteger edgeSeq = new AtomicInteger(graph.getEdges().size());
            cu.findAll(MethodDeclaration.class).forEach(method -> {
                parseSwitchTransitions(method, states, graph, edgeSeq);
                parseExplicitTransitionCalls(method, states, graph, edgeSeq);
                parseLinearStateAssignments(method, states, graph, edgeSeq);
            });
        } catch (Exception e) {
            log.warning("Failed to parse " + file + " as state machine: " + e.getMessage());
        }
    }

    private Set<String> collectStates(CompilationUnit cu, Graph graph) {
        Set<String> states = new LinkedHashSet<>();
        cu.findAll(EnumDeclaration.class).forEach(enumDecl ->
                enumDecl.getEntries().forEach(entry -> {
                    String state = entry.getNameAsString();
                    states.add(state);
                    graph.addNodeIfAbsent(new Node(state, NodeType.STATE, state));
                }));
        return states;
    }

    private void parseSwitchTransitions(MethodDeclaration method, Set<String> states,
                                        Graph graph, AtomicInteger edgeSeq) {
        method.findAll(SwitchEntry.class).forEach(entry -> {
            Set<String> sources = new LinkedHashSet<>();
            entry.getLabels().forEach(label -> stateName(label, states).ifPresent(sources::add));
            if (sources.isEmpty()) {
                return;
            }

            Set<String> targets = new LinkedHashSet<>();
            entry.findAll(AssignExpr.class).forEach(assign -> {
                if (looksLikeStateTarget(assign.getTarget(), Set.of())) {
                    stateName(assign.getValue(), states).ifPresent(targets::add);
                }
            });
            entry.findAll(ReturnStmt.class).forEach(ret ->
                    ret.getExpression().flatMap(expr -> stateName(expr, states)).ifPresent(targets::add));
            entry.findAll(YieldStmt.class).forEach(yield ->
                    stateName(yield.getExpression(), states).ifPresent(targets::add));

            for (String source : sources) {
                for (String target : targets) {
                    addTransition(graph, source, target, method.getNameAsString(), edgeSeq);
                }
            }
        });
    }

    private void parseExplicitTransitionCalls(MethodDeclaration method, Set<String> states,
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
            addTransition(graph, source.get(), target.get(), label, edgeSeq);
        });
    }

    private void parseLinearStateAssignments(MethodDeclaration method, Set<String> states,
                                             Graph graph, AtomicInteger edgeSeq) {
        // Collect names of variables (fields or locals) that are initialised to a known state value
        Set<String> stateVarNames = new LinkedHashSet<>();
        method.findAll(VariableDeclarator.class).forEach(v -> {
            if (v.getInitializer().flatMap(expr -> stateName(expr, states)).isPresent()) {
                stateVarNames.add(v.getNameAsString().toLowerCase(Locale.ROOT));
            }
        });
        String[] previous = {initialStateName(method, states)
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
                addTransition(graph, previous[0], target.get(), method.getNameAsString(), edgeSeq);
            }
            previous[0] = target.get();
        });
    }

    private Optional<String> initialStateName(MethodDeclaration method, Set<String> states) {
        return method.findAncestor(com.github.javaparser.ast.body.TypeDeclaration.class)
                .flatMap(type -> type.findAll(FieldDeclaration.class).stream()
                        .flatMap(field -> field.getVariables().stream())
                        .filter(var -> var.getNameAsString().toLowerCase(Locale.ROOT).contains("state"))
                        .flatMap(var -> var.getInitializer().flatMap(expr -> stateName(expr, states)).stream())
                        .findFirst());
    }

    private Optional<String> localVarInitialState(MethodDeclaration method, Set<String> states,
                                                   Set<String> stateVarNames) {
        return method.findAll(VariableDeclarator.class).stream()
                .filter(v -> {
                    String nameLower = v.getNameAsString().toLowerCase(Locale.ROOT);
                    return nameLower.contains("state") || stateVarNames.contains(nameLower);
                })
                .flatMap(v -> v.getInitializer().flatMap(expr -> stateName(expr, states)).stream())
                .findFirst();
    }

    private Optional<String> stateName(Expression expr, Set<String> states) {
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

    private boolean looksLikeStateTarget(Expression expr, Set<String> stateVarNames) {
        String text = expr.toString().toLowerCase(Locale.ROOT);
        return text.equals("state") || text.endsWith(".state") || text.contains("state")
                || stateVarNames.contains(text);
    }

    private void addTransition(Graph graph, String source, String target,
                               String label, AtomicInteger edgeSeq) {
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
