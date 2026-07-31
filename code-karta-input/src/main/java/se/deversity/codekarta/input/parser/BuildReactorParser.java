package se.deversity.codekarta.input.parser;

import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;
import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.EdgeType;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Node;
import se.deversity.codekarta.core.model.NodeType;
import se.deversity.vibetags.annotations.AIArchitecture;
import se.deversity.vibetags.annotations.AIContext;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Reads a build-tool reactor — Maven {@code <modules>} or Gradle {@code include(...)} — and
 * produces the same MODULE/REQUIRES shape that {@link ModuleInfoParser} produces from JPMS.
 *
 * <p>The cross-module question ("which of our modules depends on which?") is asked far more
 * often in build-tool multi-module projects than in JPMS ones, and most Java repositories —
 * including code-karta's own downstream consumers — have a reactor but no {@code
 * module-info.java} anywhere. Answering that question only for JPMS meant answering it for
 * almost nobody.
 *
 * <p>Two edge kinds come out of a reactor:
 * <ul>
 *   <li>{@code HAS} — the aggregator declares this module. This is containment, not dependency.</li>
 *   <li>{@code REQUIRES} — this module declares a dependency on a sibling in the same reactor.
 *       Dependencies outside the reactor (third-party, JDK) are dropped: they are numerous,
 *       and none of them answer the cross-module question.</li>
 * </ul>
 */
@AIContext(
    focus = "Reactor membership comes from the build files, never from the directory layout: a directory holding a pom.xml is not a module unless some aggregator lists it. Only intra-reactor dependencies become REQUIRES edges.",
    avoids = "Resolving property placeholders, profiles, or dependencyManagement — this is a structural read, not a build. Following <module> or include() paths outside the reactor root."
)
@AIArchitecture(belongsTo = "input", cannotReference = {"layout", "render", "cli"})
public class BuildReactorParser {

    private static final Logger log = Logger.getLogger(BuildReactorParser.class.getName());

    /** Guards against a pom that lists itself, directly or through a cycle of aggregators. */
    private static final int MAX_REACTOR_DEPTH = 32;

    /** {@code include("a", ":b:c")} — Kotlin and Groovy parenthesised form. */
    private static final Pattern GRADLE_INCLUDE_CALL =
            Pattern.compile("\\binclude\\s*\\(([^)]*)\\)", Pattern.DOTALL);
    /** {@code include ':a', ':b'} — Groovy command-syntax form. */
    private static final Pattern GRADLE_INCLUDE_BARE =
            Pattern.compile("(?m)^\\s*include\\s+([\"':][^\\n]*)$");
    private static final Pattern QUOTED = Pattern.compile("[\"']([^\"']+)[\"']");
    private static final Pattern GRADLE_ROOT_NAME =
            Pattern.compile("rootProject\\.name\\s*=\\s*[\"']([^\"']+)[\"']");
    /** {@code implementation(project(":a"))} and the Groovy {@code project ':a'} spelling. */
    private static final Pattern GRADLE_PROJECT_REF =
            Pattern.compile("\\bproject\\s*\\(?\\s*[\"']([:\\w.\\-]+)[\"']");

    /**
     * Reads whichever reactor the directory declares, preferring Maven when both are present.
     *
     * @return a MODULE graph, or an empty graph when the directory declares no reactor
     */
    public Graph parse(Path inputRoot) {
        Graph graph = new Graph();
        if (inputRoot == null || !Files.isDirectory(inputRoot)) {
            return graph;
        }
        // Absolutise before anything else. The reactor walk needs each pom's parent directory
        // to resolve its <module> entries against, and the caller's spelling decides whether
        // there is one: `--input .` normalises to the bare path "pom.xml", whose getParent()
        // is null, and every module then looks unreadable. Which is exactly how this was found
        // — against a real repo, where the Gradle fallback quietly covered for the failure.
        Path root = inputRoot.toAbsolutePath().normalize();
        if (Files.isRegularFile(root.resolve("pom.xml"))) {
            parseMaven(root, graph);
        }
        if (graph.getNodes().isEmpty()) {
            parseGradle(root, graph);
        }
        return graph;
    }

    // ------------------------------------------------------------------ Maven

    private void parseMaven(Path root, Graph graph) {
        // artifactId → its own pom, in reactor declaration order
        Map<String, Path> reactor = new LinkedHashMap<>();
        // aggregator artifactId → the artifactIds it declares
        Map<String, List<String>> aggregation = new LinkedHashMap<>();

        Deque<Path> queue = new ArrayDeque<>();
        Set<Path> visited = new LinkedHashSet<>();
        queue.add(root.resolve("pom.xml"));

        int depth = 0;
        while (!queue.isEmpty() && depth++ < MAX_REACTOR_DEPTH * MAX_REACTOR_DEPTH) {
            Path pom = queue.poll().normalize();
            if (!visited.add(pom)) {
                continue;
            }
            Document doc = readXml(pom);
            if (doc == null) {
                continue;
            }
            Element project = doc.getDocumentElement();
            String artifactId = childText(project, "artifactId");
            if (artifactId == null) {
                log.warning(() -> "No <artifactId> in " + pom + ", skipping it.");
                continue;
            }
            reactor.put(artifactId, pom);

            Path base = pom.getParent();
            for (String module : directChildTexts(project, "modules", "module")) {
                Path modulePom = resolveModulePom(root, base, module);
                if (modulePom == null) {
                    log.warning(() -> "Module '" + module + "' declared in " + pom + " is not readable, skipping it.");
                    continue;
                }
                aggregation.computeIfAbsent(artifactId, k -> new ArrayList<>()).add(modulePom.toString());
                queue.add(modulePom);
            }
        }

        if (reactor.size() < 2) {
            // A single pom with no modules is not a reactor; saying so beats drawing one box.
            return;
        }

        for (String artifactId : reactor.keySet()) {
            graph.addNodeIfAbsent(new Node(artifactId, NodeType.MODULE, artifactId));
        }

        // Containment: rewrite the recorded pom paths back to artifactIds now that all are known.
        Map<Path, String> byPom = new LinkedHashMap<>();
        reactor.forEach((id, pom) -> byPom.put(pom, id));
        aggregation.forEach((parent, children) -> {
            for (String childPom : children) {
                String child = byPom.get(Path.of(childPom));
                if (child != null) {
                    addEdge(graph, parent, child, EdgeType.HAS);
                }
            }
        });

        // Dependency: only edges whose target is a sibling in this same reactor.
        reactor.forEach((artifactId, pom) -> {
            Document doc = readXml(pom);
            if (doc == null) {
                return;
            }
            for (String dep : dependencyArtifactIds(doc.getDocumentElement())) {
                if (!dep.equals(artifactId) && reactor.containsKey(dep)) {
                    addEdge(graph, artifactId, dep, EdgeType.REQUIRES);
                }
            }
        });
    }

    /**
     * Resolves a {@code <module>} entry, which may name a directory or a pom file directly.
     *
     * @return the module's pom, or {@code null} when it is missing or escapes the reactor root
     */
    private Path resolveModulePom(Path root, Path base, String module) {
        if (base == null || module.isBlank()) {
            return null;
        }
        Path candidate = base.resolve(module).normalize();
        // A <module> reaching outside the tree the caller pointed at is not something to follow.
        if (!candidate.startsWith(root.normalize())) {
            log.warning(() -> "Module '" + module + "' resolves outside " + root + ", skipping it.");
            return null;
        }
        if (Files.isDirectory(candidate)) {
            candidate = candidate.resolve("pom.xml");
        }
        return Files.isRegularFile(candidate) ? candidate : null;
    }

    private List<String> dependencyArtifactIds(Element project) {
        List<String> ids = new ArrayList<>();
        for (Element dependencies : directChildren(project, "dependencies")) {
            for (Element dependency : directChildren(dependencies, "dependency")) {
                String id = childText(dependency, "artifactId");
                if (id != null) {
                    ids.add(id);
                }
            }
        }
        return ids;
    }

    // ------------------------------------------------------------------ Gradle

    private void parseGradle(Path root, Graph graph) {
        Path settings = firstExisting(root, "settings.gradle.kts", "settings.gradle");
        if (settings == null) {
            return;
        }
        String source = readText(settings);
        if (source == null) {
            return;
        }

        // ":a:b" is Gradle's project path; the last segment is the project's name.
        Map<String, Path> projects = new LinkedHashMap<>();
        for (String path : includedPaths(source)) {
            String name = path.substring(path.lastIndexOf(':') + 1);
            if (name.isEmpty()) {
                continue;
            }
            Path dir = root;
            for (String segment : path.split(":")) {
                if (!segment.isEmpty()) {
                    dir = dir.resolve(segment);
                }
            }
            projects.put(name, dir.normalize());
        }
        if (projects.isEmpty()) {
            return;
        }

        Matcher rootName = GRADLE_ROOT_NAME.matcher(source);
        String aggregator = rootName.find() ? rootName.group(1) : String.valueOf(root.normalize().getFileName());

        graph.addNodeIfAbsent(new Node(aggregator, NodeType.MODULE, aggregator));
        projects.keySet().forEach(name -> graph.addNodeIfAbsent(new Node(name, NodeType.MODULE, name)));
        projects.keySet().forEach(name -> addEdge(graph, aggregator, name, EdgeType.HAS));

        projects.forEach((name, dir) -> {
            Path buildFile = firstExisting(dir, "build.gradle.kts", "build.gradle");
            if (buildFile == null) {
                return;
            }
            String build = readText(buildFile);
            if (build == null) {
                return;
            }
            Matcher refs = GRADLE_PROJECT_REF.matcher(build);
            while (refs.find()) {
                String target = refs.group(1);
                target = target.substring(target.lastIndexOf(':') + 1);
                if (!target.equals(name) && projects.containsKey(target)) {
                    addEdge(graph, name, target, EdgeType.REQUIRES);
                }
            }
        });
    }

    private Set<String> includedPaths(String settingsSource) {
        Set<String> paths = new LinkedHashSet<>();
        collectQuoted(GRADLE_INCLUDE_CALL.matcher(settingsSource), paths);
        collectQuoted(GRADLE_INCLUDE_BARE.matcher(settingsSource), paths);
        return paths;
    }

    private void collectQuoted(Matcher includeMatcher, Set<String> sink) {
        while (includeMatcher.find()) {
            Matcher quoted = QUOTED.matcher(includeMatcher.group(1));
            while (quoted.find()) {
                sink.add(quoted.group(1));
            }
        }
    }

    private Path firstExisting(Path dir, String... names) {
        for (String name : names) {
            Path candidate = dir.resolve(name);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        return null;
    }

    // ------------------------------------------------------------------ helpers

    private void addEdge(Graph graph, String source, String target, String type) {
        graph.addEdge(new Edge(source + "-" + type.toLowerCase(java.util.Locale.ROOT) + "-" + target,
                source, target, type));
    }

    private String readText(Path file) {
        try {
            return Files.readString(file);
        } catch (IOException | RuntimeException e) {
            log.warning(() -> "Failed to read " + file + ": " + e.getMessage());
            return null;
        }
    }

    /**
     * Parses a build file as XML with entity resolution switched off.
     *
     * <p>The input is somebody else's pom, so it is untrusted: a DOCTYPE with an external
     * entity would otherwise turn "draw me a module diagram" into a file read.
     */
    private Document readXml(Path file) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_DTD, "");
            factory.setAttribute(XMLConstants.ACCESS_EXTERNAL_SCHEMA, "");
            factory.setXIncludeAware(false);
            factory.setExpandEntityReferences(false);
            factory.setNamespaceAware(false);
            return factory.newDocumentBuilder().parse(file.toFile());
        } catch (Exception e) {
            log.warning(() -> "Failed to parse " + file + ": " + e.getMessage());
            return null;
        }
    }

    /** Direct-child elements by tag name — {@code getElementsByTagName} would reach into nested poms. */
    private List<Element> directChildren(Element parent, String tag) {
        List<Element> found = new ArrayList<>();
        NodeList children = parent.getChildNodes();
        for (int i = 0; i < children.getLength(); i++) {
            if (children.item(i) instanceof Element element && tag.equals(element.getTagName())) {
                found.add(element);
            }
        }
        return found;
    }

    private String childText(Element parent, String tag) {
        List<Element> matches = directChildren(parent, tag);
        if (matches.isEmpty()) {
            return null;
        }
        String text = matches.get(0).getTextContent();
        return text == null || text.isBlank() ? null : text.trim();
    }

    private List<String> directChildTexts(Element parent, String containerTag, String itemTag) {
        List<String> texts = new ArrayList<>();
        for (Element container : directChildren(parent, containerTag)) {
            for (Element item : directChildren(container, itemTag)) {
                String text = item.getTextContent();
                if (text != null && !text.isBlank()) {
                    texts.add(text.trim());
                }
            }
        }
        return texts;
    }
}
