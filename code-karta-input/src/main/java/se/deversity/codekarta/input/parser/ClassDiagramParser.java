package se.deversity.codekarta.input.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParserConfiguration;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Group;
import se.deversity.codekarta.core.model.Node;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

@se.deversity.vibetags.annotations.AIContext(
    focus = "Generic-type stripping: rawType() must be called before SKIP_TYPES lookup so 'List<Node>' → 'List' and gets filtered. Node.properties is populated with truncated field/method summaries for UML compartments. HAS edge labels carry the field name.",
    avoids = "Bypassing SKIP_TYPES for stdlib types — class diagrams quickly become unreadable with List/Map/String nodes. Populating Node.properties for externally-referenced stub nodes (only populate for types whose source is in this parse run)."
)
@se.deversity.vibetags.annotations.AIArchitecture(belongsTo = "input", cannotReference = {"layout", "render", "cli"})
public class ClassDiagramParser {

    private static final Logger log = Logger.getLogger(ClassDiagramParser.class.getName());
    private static final JavaParser PARSER = new JavaParser(
            new ParserConfiguration().setLanguageLevel(ParserConfiguration.LanguageLevel.JAVA_21));

    private static final Set<String> SKIP_TYPES = Set.of(
            "int", "long", "double", "float", "boolean", "byte", "short", "char",
            "String", "Object", "Integer", "Long", "Double", "Float", "Boolean",
            "Byte", "Short", "Character", "List", "Map", "Set", "Queue",
            "Optional", "Stream", "Collection", "Iterable", "void", "Void",
            "StringBuilder", "StringBuffer", "Number", "Comparable", "Serializable");

    private final Set<String> customExcludes;

    /**
     * Compartment lines kept per section before the rest collapse into "…(+N more)".
     *
     * <p>Six is the right default for a diagram of a large package, where the answer to
     * "what is in here" matters more than any one member. It is the wrong default for a
     * diagram of five classes, where the members <em>are</em> the content — hence
     * {@link #DEFAULT_MAX_MEMBERS} being a default rather than the rule.
     */
    public static final int DEFAULT_MAX_MEMBERS = 6;

    /** Any non-positive value means "show every member". */
    public static final int UNLIMITED_MEMBERS = 0;

    private final int maxMembers;

    public ClassDiagramParser() {
        this(java.util.Collections.emptySet());
    }

    public ClassDiagramParser(Set<String> customExcludes) {
        this(customExcludes, DEFAULT_MAX_MEMBERS);
    }

    /**
     * @param maxMembers field/method lines to keep per compartment; {@link #UNLIMITED_MEMBERS}
     *                   (or any negative value) keeps all of them
     */
    public ClassDiagramParser(Set<String> customExcludes, int maxMembers) {
        this.customExcludes = customExcludes != null ? customExcludes : java.util.Collections.emptySet();
        this.maxMembers = maxMembers;
    }

    public Graph parse(Path sourceDirectory) {
        Graph graph = new Graph();
        List<CompilationUnit> units = new ArrayList<>();
        try (var stream = Files.walk(sourceDirectory)) {
            List<Path> javaFiles = stream
                    .filter(p -> p.toString().endsWith(".java"))
                    .toList();
            for (Path file : javaFiles) {
                try {
                    units.add(PARSER.parse(Files.readString(file)).getResult().orElseThrow());
                } catch (Exception e) {
                    log.warning("Failed to parse " + file + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warning("Error walking directory " + sourceDirectory + ": " + e.getMessage());
        }

        // Pass 1: types declared in this parse run — relationship targets outside
        // this set are external (JDK, third-party) and are kept out of the diagram
        Set<String> declaredTypes = new HashSet<>();
        for (CompilationUnit cu : units) {
            cu.getTypes().forEach(t -> declaredTypes.add(t.getNameAsString()));
        }

        // Pass 2: nodes, package groups, and edges
        for (CompilationUnit cu : units) {
            try {
                String packageName = cu.getPackageDeclaration()
                        .map(pd -> pd.getNameAsString())
                        .orElse(null);
                for (TypeDeclaration<?> type : cu.getTypes()) {
                    processType(type, graph, packageName, declaredTypes);
                }
            } catch (Exception e) {
                log.warning("Failed to process compilation unit: " + e.getMessage());
            }
        }
        return graph;
    }

    private void processType(TypeDeclaration<?> type, Graph graph, String packageName, Set<String> declaredTypes) {
        String name = type.getNameAsString();
        if (FilterMatcher.matchesAny(name, customExcludes)) {
            return;
        }
        String nodeType = (type instanceof ClassOrInterfaceDeclaration coid && coid.isInterface())
                ? "INTERFACE"
                : "CLASS";

        graph.addNodeIfAbsent(new Node(name, nodeType, name));
        // Update the stored node (may have been added as a stub by another file's reference)
        Node typeNode = graph.findNode(name);
        if (typeNode != null) {
            typeNode.setType(nodeType);
            typeNode.setProperties(buildProperties(type));
        }

        if (packageName != null) {
            String groupId = "package-" + packageName;
            Group pkgGroup = graph.getGroups().stream()
                    .filter(g -> groupId.equals(g.getId()))
                    .findFirst()
                    .orElse(null);
            if (pkgGroup == null) {
                pkgGroup = new Group(groupId, packageName);
                graph.addGroup(pkgGroup);
            }
            if (!pkgGroup.getMemberIds().contains(name)) {
                pkgGroup.addMember(name);
            }
        }

        if (type instanceof ClassOrInterfaceDeclaration coid) {
            for (var extended : coid.getExtendedTypes()) {
                String parent = extended.getNameAsString();
                // Only link to types declared in this parse run — external supertypes
                // (RuntimeException, AutoCloseable, …) would clutter the diagram
                if (!declaredTypes.contains(parent) || FilterMatcher.matchesAny(parent, customExcludes)) {
                    continue;
                }
                graph.addNodeIfAbsent(new Node(parent, "CLASS", parent));
                graph.addEdge(new Edge(name + "-extends-" + parent, name, parent, "EXTENDS"));
            }
            for (var implemented : coid.getImplementedTypes()) {
                String iface = implemented.getNameAsString();
                if (!declaredTypes.contains(iface) || FilterMatcher.matchesAny(iface, customExcludes)) {
                    continue;
                }
                graph.addNodeIfAbsent(new Node(iface, "INTERFACE", iface));
                graph.addEdge(new Edge(name + "-implements-" + iface, name, iface, "IMPLEMENTS"));
            }
            for (FieldDeclaration field : coid.getFields()) {
                String fullType  = field.getElementType().asString();
                String rawType   = rawType(fullType);
                String fieldName = field.getVariables().get(0).getNameAsString();

                if (declaredTypes.contains(rawType) && !SKIP_TYPES.contains(rawType)
                        && !FilterMatcher.matchesAny(rawType, customExcludes)) {
                    // Direct domain type: Engine engine → HAS Engine
                    graph.addNodeIfAbsent(new Node(rawType, "CLASS", rawType));
                    Edge hasEdge = new Edge(
                            name + "-has-" + fieldName + "-" + rawType,
                            name, rawType, "HAS");
                    hasEdge.setLabel(fieldName);
                    graph.addEdge(hasEdge);
                } else {
                    // Container of domain type: List<Node> → HAS Node, Map<String,Node> → HAS Node
                    String innerType = innerGenericType(fullType);
                    if (innerType != null && declaredTypes.contains(innerType)
                            && !SKIP_TYPES.contains(innerType)
                            && !FilterMatcher.matchesAny(innerType, customExcludes)) {
                        graph.addNodeIfAbsent(new Node(innerType, "CLASS", innerType));
                        Edge hasEdge = new Edge(
                                name + "-has-" + fieldName + "-" + innerType,
                                name, innerType, "HAS");
                        hasEdge.setLabel(fieldName);
                        graph.addEdge(hasEdge);
                    }
                }
            }
        }
    }

    private Map<String, String> buildProperties(TypeDeclaration<?> type) {
        Map<String, String> props = new HashMap<>();

        List<String> fieldLines = new ArrayList<>();
        for (FieldDeclaration field : type.getFields()) {
            String displayType = field.getElementType().asString();
            for (var v : field.getVariables()) {
                fieldLines.add(v.getNameAsString() + ": " + displayType);
            }
        }
        if (!fieldLines.isEmpty()) {
            props.put("fields", formatList(fieldLines));
        }

        if (type instanceof ClassOrInterfaceDeclaration coid) {
            List<String> methodLines = new ArrayList<>();
            for (MethodDeclaration m : coid.getMethods()) {
                StringBuilder sig = new StringBuilder(m.getNameAsString()).append("(");
                var params = m.getParameters();
                for (int i = 0; i < params.size(); i++) {
                    if (i > 0) sig.append(", ");
                    sig.append(params.get(i).getType().asString());
                }
                sig.append("): ").append(m.getType().asString());
                methodLines.add(sig.toString());
            }
            if (!methodLines.isEmpty()) {
                props.put("methods", formatList(methodLines));
            }
        }

        // Detect constants-class pattern: only static-final fields, no instance methods
        if (type instanceof ClassOrInterfaceDeclaration coid) {
            boolean isConstantsClass = !type.getFields().isEmpty()
                    && coid.getMethods().isEmpty()
                    && type.getFields().stream().allMatch(f -> f.isStatic() && f.isFinal());
            if (isConstantsClass) {
                props.put("stereotype", "«constants»");
            }
        }

        return props;
    }

    private String formatList(List<String> lines) {
        if (maxMembers <= UNLIMITED_MEMBERS || lines.size() <= maxMembers) {
            return String.join("\n", lines);
        }
        int extra = lines.size() - maxMembers;
        List<String> truncated = new ArrayList<>(lines.subList(0, maxMembers));
        truncated.add("…(+" + extra + " more)");
        return String.join("\n", truncated);
    }

    /** Strip generic type parameters: {@code List<Node>} → {@code List}. */
    public static String rawType(String typeName) {
        int lt = typeName.indexOf('<');
        return lt >= 0 ? typeName.substring(0, lt) : typeName;
    }

    /**
     * Extract the last generic type parameter: {@code Map<String, Node>} → {@code Node},
     * {@code List<Node>} → {@code Node}.  Returns {@code null} if no type parameter is present.
     */
    public static String innerGenericType(String typeName) {
        int lt = typeName.indexOf('<');
        int gt = typeName.lastIndexOf('>');
        if (lt < 0 || gt <= lt) return null;
        String params = typeName.substring(lt + 1, gt);
        // For Map<K,V> take the value (last) param; for single-param types like List<T> there is only one
        int comma = params.lastIndexOf(',');
        String inner = comma >= 0 ? params.substring(comma + 1).trim() : params.trim();
        return rawType(inner); // strip further generics, e.g. List<List<X>>
    }
}
