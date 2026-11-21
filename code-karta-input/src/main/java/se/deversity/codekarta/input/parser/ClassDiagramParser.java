package se.deversity.codekarta.input.parser;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.nodeTypes.NodeWithImplements;
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
        List<ParsedUnit> units = parseUnits(sourceDirectory);
        return buildGraph(units, indexOf(units));
    }

    /**
     * One class diagram per directory holding a {@code .java} file, each drawing only the types
     * declared directly in that directory, keyed by directory in sorted order.
     *
     * <p>The whole tree is parsed once and indexed, so a field or supertype in one package that
     * names a type in another still resolves. That type appears as a stub node: its label, no
     * members, no package group. Drawing each directory by parsing it recursively instead
     * repeated every subpackage's classes in each parent package's diagram, so the top-level
     * diagram was the whole tree again (issue #232).
     */
    public Map<Path, Graph> parsePerDirectory(Path sourceDirectory) {
        List<ParsedUnit> units = parseUnits(sourceDirectory);
        TypeIndex index = indexOf(units);
        Map<Path, List<ParsedUnit>> byDirectory = new java.util.TreeMap<>();
        for (ParsedUnit unit : units) {
            Path dir = unit.file().getParent();
            if (dir != null) {
                byDirectory.computeIfAbsent(dir, k -> new ArrayList<>()).add(unit);
            }
        }
        Map<Path, Graph> graphs = new java.util.LinkedHashMap<>();
        byDirectory.forEach((dir, dirUnits) -> graphs.put(dir, buildGraph(dirUnits, index)));
        return graphs;
    }

    private record ParsedUnit(Path file, CompilationUnit cu) {}

    private List<ParsedUnit> parseUnits(Path sourceDirectory) {
        List<ParsedUnit> units = new ArrayList<>();
        JavaParser parser = ParserSupport.newJava21Parser();
        try {
            // SourceTree sorts: walk order is filesystem-defined (NTFS name order, ext4 hash
            // order), and node order reaches the rendered SVG that consumers commit.
            List<Path> javaFiles = SourceTree.javaFiles(sourceDirectory, includeTests);
            for (Path file : javaFiles) {
                try {
                    units.add(new ParsedUnit(file, parser.parse(Files.readString(file)).getResult().orElseThrow()));
                } catch (Exception e) {
                    log.warning("Failed to parse " + file + ": " + e.getMessage());
                }
            }
        } catch (IOException e) {
            log.warning("Error walking directory " + sourceDirectory + ": " + e.getMessage());
        }
        return units;
    }

    /**
     * Pass 1: types declared in this parse run. Relationship targets outside this set are
     * external (JDK, third-party) and are kept out of the diagram.
     */
    private static TypeIndex indexOf(List<ParsedUnit> units) {
        TypeIndex index = new TypeIndex();
        for (ParsedUnit unit : units) {
            String packageName = packageOf(unit.cu());
            unit.cu().getTypes().forEach(t -> index.addWithMembers(packageName, null, t));
        }
        return index;
    }

    /** Pass 2: nodes, package groups and edges for {@code units}, resolved against {@code index}. */
    private Graph buildGraph(List<ParsedUnit> units, TypeIndex index) {
        Graph graph = new Graph();
        for (ParsedUnit unit : units) {
            try {
                Scope scope = new Scope(unit.cu());
                for (TypeDeclaration<?> type : unit.cu().getTypes()) {
                    processType(type, null, List.of(), graph, scope, index);
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

    /** Name of a type relative to its package: {@code Calc} or, nested, {@code Calc.Inner}. */
    private static String relativeName(@Nullable String outerRelativeName, TypeDeclaration<?> type) {
        return outerRelativeName == null
                ? type.getNameAsString()
                : outerRelativeName + "." + type.getNameAsString();
    }

    /** Member types of {@code type}: nested classes, interfaces, records and enums. */
    private static List<TypeDeclaration<?>> memberTypes(TypeDeclaration<?> type) {
        List<TypeDeclaration<?>> members = new ArrayList<>();
        for (BodyDeclaration<?> member : type.getMembers()) {
            if (member instanceof TypeDeclaration<?> nested) {
                members.add(nested);
            }
        }
        return members;
    }

    /** Every type declared in this parse run, by node id and by simple name. */
    private static final class TypeIndex {
        private final Map<String, String> labelsById = new HashMap<>();
        private final Map<String, Set<String>> idsBySimpleName = new HashMap<>();

        /** Indexes {@code type} and, recursively, its member types. */
        void addWithMembers(@Nullable String packageName, @Nullable String outerRelativeName,
                            TypeDeclaration<?> type) {
            String relative = relativeName(outerRelativeName, type);
            String id = typeId(packageName, relative);
            labelsById.put(id, relative);
            idsBySimpleName.computeIfAbsent(type.getNameAsString(), k -> new LinkedHashSet<>()).add(id);
            for (TypeDeclaration<?> nested : memberTypes(type)) {
                addWithMembers(packageName, relative, nested);
            }
        }

        boolean contains(String id) {
            return labelsById.containsKey(id);
        }

        /** Display name of a declared type: its name relative to its package. */
        String label(String id) {
            return labelsById.getOrDefault(id, simpleName(id));
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
         * no type of this parse run or could name several. Java's own precedence: member types
         * of the enclosing types (innermost first), then a single-type import, then the unit's
         * package, then on-demand imports. A dotted name is either qualified ({@code b.Config})
         * or a member-type path ({@code Outer.Inner}) whose head resolves like a simple name. A
         * simple name that none of those settle still resolves when exactly one declared type
         * carries it, since diagrams are drawn from partial source trees too; when several do,
         * no edge beats a wrong one.
         */
        @Nullable String resolve(String typeName, TypeIndex index, List<String> enclosingIds) {
            int dot = typeName.indexOf('.');
            if (dot >= 0) {
                if (index.contains(typeName)) {
                    return typeName;
                }
                String head = resolve(typeName.substring(0, dot), index, enclosingIds);
                String member = head != null ? head + typeName.substring(dot) : null;
                return member != null && index.contains(member) ? member : null;
            }
            for (String enclosing : enclosingIds) {
                String memberType = enclosing + "." + typeName;
                if (index.contains(memberType)) {
                    return memberType;
                }
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

    /**
     * Adds {@code type}, its relationships, and then its member types.
     *
     * @param enclosingIds ids of the types around {@code type}, innermost first
     */
    private void processType(TypeDeclaration<?> type, @Nullable String outerRelativeName,
                             List<String> enclosingIds, Graph graph, Scope scope, TypeIndex index) {
        String simple = type.getNameAsString();
        if (FilterMatcher.matchesAny(simple, customExcludes)) {
            return;
        }
        String relative = relativeName(outerRelativeName, type);
        String id = typeId(scope.packageName, relative);
        List<String> inScope = new ArrayList<>(enclosingIds.size() + 1);
        inScope.add(id);
        inScope.addAll(enclosingIds);
        String nodeType = (type instanceof ClassOrInterfaceDeclaration coid && coid.isInterface())
                ? "INTERFACE"
                : "CLASS";

        graph.addNodeIfAbsent(new Node(id, nodeType, relative));
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
                String parent = supertypeTarget(extended.getNameWithScope(), inScope, scope, index);
                if (parent == null) {
                    continue;
                }
                graph.addNodeIfAbsent(new Node(parent, "CLASS", index.label(parent)));
                graph.addEdge(new Edge(id + "-extends-" + parent, id, parent, "EXTENDS"));
            }
        }
        // Classes, interfaces, records and enums; annotation types have neither supertypes
        // nor fields worth an edge
        if (type instanceof NodeWithImplements<?> implementing) {
            for (var implemented : implementing.getImplementedTypes()) {
                String iface = supertypeTarget(implemented.getNameWithScope(), inScope, scope, index);
                if (iface == null) {
                    continue;
                }
                graph.addNodeIfAbsent(new Node(iface, "INTERFACE", index.label(iface)));
                graph.addEdge(new Edge(id + "-implements-" + iface, id, iface, "IMPLEMENTS"));
            }
            for (Member member : members(type)) {
                // Direct domain type: Engine engine → HAS Engine
                String target = fieldTarget(rawType(member.type()), inScope, scope, index);
                if (target == null) {
                    // Container of domain type: List<Node> → HAS Node, Map<String,Node> → HAS Node
                    String innerType = innerGenericType(member.type());
                    target = innerType != null ? fieldTarget(innerType, inScope, scope, index) : null;
                }
                if (target != null) {
                    graph.addNodeIfAbsent(new Node(target, "CLASS", index.label(target)));
                    Edge hasEdge = new Edge(id + "-has-" + member.name() + "-" + target, id, target, "HAS");
                    hasEdge.setLabel(member.name());
                    graph.addEdge(hasEdge);
                }
            }
        }

        for (TypeDeclaration<?> nested : memberTypes(type)) {
            processType(nested, relative, inScope, graph, scope, index);
        }
    }

    /** A field or record component: its name and its declared type as written. */
    private record Member(String name, String type) {}

    /**
     * The members a HAS edge can start from, in declaration order: record components first,
     * then fields. Only the first variable of a multi-variable field declaration is used, as
     * one {@code int a, b;} declaration has one type.
     */
    private static List<Member> members(TypeDeclaration<?> type) {
        List<Member> members = new ArrayList<>();
        if (type instanceof RecordDeclaration rec) {
            for (Parameter component : rec.getParameters()) {
                members.add(new Member(component.getNameAsString(), component.getType().asString()));
            }
        }
        for (FieldDeclaration field : type.getFields()) {
            members.add(new Member(field.getVariables().get(0).getNameAsString(),
                    field.getElementType().asString()));
        }
        return members;
    }

    /**
     * The node id a supertype points at, or {@code null} for an excluded name or a type this
     * parse run does not declare. {@link #SKIP_TYPES} does not apply: a declared {@code List}
     * interface is a real supertype (the JDK corpus test pins {@code ArrayList -> List}).
     */
    private @Nullable String supertypeTarget(String typeName, List<String> enclosingIds,
                                             Scope scope, TypeIndex index) {
        return FilterMatcher.matchesAny(simpleName(typeName), customExcludes)
                ? null
                : scope.resolve(typeName, index, enclosingIds);
    }

    /**
     * The node id a field type points at, or {@code null} when it is not drawn: a stdlib type,
     * an excluded name, or a type this parse run does not declare. {@code typeName} must
     * already be raw ({@link #rawType}) so {@code List<Node>} is checked as {@code List}.
     */
    private @Nullable String fieldTarget(String typeName, List<String> enclosingIds,
                                         Scope scope, TypeIndex index) {
        return SKIP_TYPES.contains(simpleName(typeName))
                ? null
                : supertypeTarget(typeName, enclosingIds, scope, index);
    }

    private Map<String, String> buildProperties(TypeDeclaration<?> type) {
        Map<String, String> props = new HashMap<>();

        List<String> fieldLines = new ArrayList<>();
        if (type instanceof RecordDeclaration rec) {
            for (Parameter component : rec.getParameters()) {
                fieldLines.add(component.getNameAsString() + ": " + component.getType().asString());
            }
        }
        for (FieldDeclaration field : type.getFields()) {
            String displayType = field.getElementType().asString();
            for (var v : field.getVariables()) {
                fieldLines.add(v.getNameAsString() + ": " + displayType);
            }
        }
        if (!fieldLines.isEmpty()) {
            props.put("fields", formatList(fieldLines));
        }

        List<String> methodLines = new ArrayList<>();
        for (MethodDeclaration m : type.getMethods()) {
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

        // NodeType is locked, so records and enums stay CLASS and say what they are through the
        // stereotype override SvgRenderer already draws.
        if (type instanceof RecordDeclaration) {
            props.put("stereotype", "«record»");
        } else if (type instanceof EnumDeclaration) {
            props.put("stereotype", "«enum»");
        } else if (type instanceof ClassOrInterfaceDeclaration coid) {
            // Constants-class pattern: only static-final fields, no methods
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
