package se.deversity.codekarta.input.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Group;
import se.deversity.codekarta.core.model.Node;
import se.deversity.vibetags.annotations.AIArchitecture;
import se.deversity.vibetags.annotations.AIContext;
import se.deversity.vibetags.annotations.AIParallelTests;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

@AIContext(
    focus = "Generic-type stripping: rawType() must be called before SKIP_TYPES lookup so 'List<Node>' → 'List' and gets filtered. Node.properties is populated with truncated field/method summaries for UML compartments. HAS edge labels carry the field name.",
    avoids = "Bypassing SKIP_TYPES for stdlib types — class diagrams quickly become unreadable with List/Map/String nodes. Populating Node.properties for externally-referenced stub nodes (only populate for types whose source is in this parse run)."
)
@AIArchitecture(belongsTo = "input", cannotReference = {"layout", "render", "cli"})
@AIParallelTests(reason = "ClassDiagramParserTest builds each fixture in its own @TempDir and asserts on the returned Graph. Never reach for a shared static parser or a fixed scratch path — the suite must stay safe under Surefire forkCount > 1.")
public class ClassDiagramParser {

    private static final Logger log = Logger.getLogger(ClassDiagramParser.class.getName());

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

    private final boolean includeTests;

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
        this(customExcludes, maxMembers, false);
    }

    /**
     * @param maxMembers   see {@link #ClassDiagramParser(Set, int)}
     * @param includeTests {@code true} to read test source sets too when the input is a project
     *                     or repository root; see {@link SourceTree}
     */
    public ClassDiagramParser(Set<String> customExcludes, int maxMembers, boolean includeTests) {
        this.customExcludes = customExcludes != null ? customExcludes : java.util.Collections.emptySet();
        this.maxMembers = maxMembers;
        this.includeTests = includeTests;
    }

    public Graph parse(Path sourceDirectory) {
        Graph graph = new Graph();
        List<CompilationUnit> units = new ArrayList<>();
        JavaParser parser = ParserSupport.newJava21Parser();
        try {
            // SourceTree sorts: walk order is filesystem-defined (NTFS name order, ext4 hash
            // order), and node order reaches the rendered SVG that consumers commit.
            List<Path> javaFiles = SourceTree.javaFiles(sourceDirectory, includeTests);
            for (Path file : javaFiles) {
                try {
                    units.add(parser.parse(Files.readString(file)).getResult().orElseThrow());
                } catch (Exception e) {
                    log.warning("Failed to parse " + file + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warning("Error walking directory " + sourceDirectory + ": " + e.getMessage());
        }

        // Pass 1: types declared in this parse run. Relationship targets outside this set are
        // external (JDK, third-party) and are kept out of the diagram.
        TypeIndex index = new TypeIndex();
        for (CompilationUnit cu : units) {
            String packageName = packageOf(cu);
            cu.getTypes().forEach(t -> index.add(packageName, t.getNameAsString()));
        }

        // Pass 2: nodes, package groups, and edges
        for (CompilationUnit cu : units) {
            try {
                Scope scope = new Scope(cu);
                for (TypeDeclaration<?> type : cu.getTypes()) {
                    processType(type, graph, scope, index);
                }
            } catch (Exception e) {
                log.warning("Failed to process compilation unit: " + e.getMessage());
            }
        }
        return graph;
    }

    private static @Nullable String packageOf(CompilationUnit cu) {
        return cu.getPackageDeclaration().map(pd -> pd.getNameAsString()).orElse(null);
    }

    /**
     * Node id of a type: its qualified name, or the simple name in the default package.
     *
     * <p>Two types that share a simple name in different packages are different nodes. Keyed by
     * simple name they merged into one box with the later file's members, a membership in both
     * package groups, and every edge to either type (issue #226). The node label stays the simple
     * name; the package group around the box says which package it is in.
     */
    static String typeId(@Nullable String packageName, String simpleName) {
        return packageName == null || packageName.isEmpty() ? simpleName : packageName + "." + simpleName;
    }

    private static String simpleName(String typeName) {
        return typeName.substring(typeName.lastIndexOf('.') + 1);
    }

    /** Every type declared in this parse run, by node id and by simple name. */
    private static final class TypeIndex {
        private final Set<String> ids = new HashSet<>();
        private final Map<String, Set<String>> idsBySimpleName = new HashMap<>();

        void add(@Nullable String packageName, String simpleName) {
            String id = typeId(packageName, simpleName);
            ids.add(id);
            idsBySimpleName.computeIfAbsent(simpleName, k -> new LinkedHashSet<>()).add(id);
        }

        boolean contains(String id) {
            return ids.contains(id);
        }

        /** The only declared type with this simple name, or {@code null} if none or several. */
        @Nullable String unique(String simpleName) {
            Set<String> candidates = idsBySimpleName.getOrDefault(simpleName, Set.of());
            return candidates.size() == 1 ? candidates.iterator().next() : null;
        }
    }

    /** What a type name written in one compilation unit can refer to: its package and imports. */
    private static final class Scope {
        final @Nullable String packageName;
        private final Map<String, String> singleTypeImports = new HashMap<>();
        private final List<String> onDemandImports = new ArrayList<>();

        Scope(CompilationUnit cu) {
            this.packageName = packageOf(cu);
            for (ImportDeclaration imp : cu.getImports()) {
                if (imp.isStatic()) {
                    continue;
                }
                String name = imp.getNameAsString();
                if (imp.isAsterisk()) {
                    onDemandImports.add(name);
                } else {
                    singleTypeImports.put(simpleName(name), name);
                }
            }
        }

        /**
         * The node id a type name written in this unit refers to, or {@code null} when it names
         * no type of this parse run or could name several. Java's own precedence: a qualified
         * name, then a single-type import, then the unit's package, then on-demand imports. A
         * simple name that none of those settle still resolves when exactly one declared type
         * carries it, since diagrams are drawn from partial source trees too; when several do,
         * no edge beats a wrong one.
         */
        @Nullable String resolve(String typeName, TypeIndex index) {
            if (typeName.indexOf('.') >= 0) {
                return index.contains(typeName) ? typeName : null;
            }
            String imported = singleTypeImports.get(typeName);
            if (imported != null) {
                return index.contains(imported) ? imported : null;
            }
            String samePackage = typeId(packageName, typeName);
            if (index.contains(samePackage)) {
                return samePackage;
            }
            String onDemand = null;
            for (String pkg : onDemandImports) {
                String candidate = typeId(pkg, typeName);
                if (index.contains(candidate)) {
                    if (onDemand != null) {
                        return null;
                    }
                    onDemand = candidate;
                }
            }
            return onDemand != null ? onDemand : index.unique(typeName);
        }
    }

    private void processType(TypeDeclaration<?> type, Graph graph, Scope scope, TypeIndex index) {
        String simple = type.getNameAsString();
        if (FilterMatcher.matchesAny(simple, customExcludes)) {
            return;
        }
        String id = typeId(scope.packageName, simple);
        String nodeType = (type instanceof ClassOrInterfaceDeclaration coid && coid.isInterface())
                ? "INTERFACE"
                : "CLASS";

        graph.addNodeIfAbsent(new Node(id, nodeType, simple));
        // Update the stored node (may have been added as a stub by another file's reference)
        Node typeNode = graph.findNode(id);
        if (typeNode != null) {
            typeNode.setType(nodeType);
            typeNode.setProperties(buildProperties(type));
        }

        if (scope.packageName != null) {
            String groupId = "package-" + scope.packageName;
            Group pkgGroup = graph.getGroups().stream()
                    .filter(g -> groupId.equals(g.getId()))
                    .findFirst()
                    .orElse(null);
            if (pkgGroup == null) {
                pkgGroup = new Group(groupId, scope.packageName);
                graph.addGroup(pkgGroup);
            }
            if (!pkgGroup.getMemberIds().contains(id)) {
                pkgGroup.addMember(id);
            }
        }

        if (type instanceof ClassOrInterfaceDeclaration coid) {
            for (var extended : coid.getExtendedTypes()) {
                // Only link to types declared in this parse run: external supertypes
                // (RuntimeException, AutoCloseable, …) would clutter the diagram
                String parent = supertypeTarget(extended.getNameWithScope(), scope, index);
                if (parent == null) {
                    continue;
                }
                graph.addNodeIfAbsent(new Node(parent, "CLASS", simpleName(parent)));
                graph.addEdge(new Edge(id + "-extends-" + parent, id, parent, "EXTENDS"));
            }
            for (var implemented : coid.getImplementedTypes()) {
                String iface = supertypeTarget(implemented.getNameWithScope(), scope, index);
                if (iface == null) {
                    continue;
                }
                graph.addNodeIfAbsent(new Node(iface, "INTERFACE", simpleName(iface)));
                graph.addEdge(new Edge(id + "-implements-" + iface, id, iface, "IMPLEMENTS"));
            }
            for (FieldDeclaration field : coid.getFields()) {
                String fullType  = field.getElementType().asString();
                String fieldName = field.getVariables().get(0).getNameAsString();

                // Direct domain type: Engine engine → HAS Engine
                String target = fieldTarget(rawType(fullType), scope, index);
                if (target == null) {
                    // Container of domain type: List<Node> → HAS Node, Map<String,Node> → HAS Node
                    String innerType = innerGenericType(fullType);
                    target = innerType != null ? fieldTarget(innerType, scope, index) : null;
                }
                if (target != null) {
                    graph.addNodeIfAbsent(new Node(target, "CLASS", simpleName(target)));
                    Edge hasEdge = new Edge(id + "-has-" + fieldName + "-" + target, id, target, "HAS");
                    hasEdge.setLabel(fieldName);
                    graph.addEdge(hasEdge);
                }
            }
        }
    }

    /**
     * The node id a supertype points at, or {@code null} for an excluded name or a type this
     * parse run does not declare. {@link #SKIP_TYPES} does not apply: a declared {@code List}
     * interface is a real supertype (the JDK corpus test pins {@code ArrayList -> List}).
     */
    private @Nullable String supertypeTarget(String typeName, Scope scope, TypeIndex index) {
        return FilterMatcher.matchesAny(simpleName(typeName), customExcludes)
                ? null
                : scope.resolve(typeName, index);
    }

    /**
     * The node id a field type points at, or {@code null} when it is not drawn: a stdlib type,
     * an excluded name, or a type this parse run does not declare. {@code typeName} must
     * already be raw ({@link #rawType}) so {@code List<Node>} is checked as {@code List}.
     */
    private @Nullable String fieldTarget(String typeName, Scope scope, TypeIndex index) {
        return SKIP_TYPES.contains(simpleName(typeName)) ? null : supertypeTarget(typeName, scope, index);
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
