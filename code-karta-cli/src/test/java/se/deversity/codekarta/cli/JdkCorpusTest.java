package se.deversity.codekarta.cli;

import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Node;
import se.deversity.codekarta.input.JavaSourceInputParser;

import java.io.IOException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;

/**
 * Runs the whole pipeline over real JDK source, from the src.zip that ships with the JDK running
 * this build.
 *
 * <p>The rest of the suite parses hand-written fixtures: eleven files in example-shipping-system
 * and thirty-five of our own. That is not enough Java to find out whether the parsers survive
 * contact with code nobody here wrote. The top level of java.util alone is 135 files of generics,
 * nested types, sealed and record declarations, switch expressions and text blocks, and it costs
 * nothing to obtain: every JDK ships it, so there is no corpus to vendor, to download, or to
 * license.
 *
 * <p>What this guards is specific. Parsers never throw. They log a warning and return a partial
 * graph, which is the documented contract, and that makes a parsing regression silent: the graph
 * quietly gets smaller and every existing test still passes. So the assertions here are a volume
 * floor, a set of structural facts that have held for java.util since 1.2, and a warning count of
 * zero. Any of the three going red means the parsers stopped seeing something they used to see.
 *
 * <p>This is a regression gate, not a bug hunt: it passed on the day it was written. It is also
 * not a correctness oracle, because there is no ground truth for whether a diagram is the right
 * diagram, so nothing here asserts what the picture looks like.
 *
 * <p>Skipped when src.zip is absent, which is what a JRE looks like. Pass -Dcorpus.required=true
 * to turn that into a failure instead. CI does, because a corpus test that silently skips is not
 * a gate.
 */
class JdkCorpusTest {

    /** Where every JDK keeps its own sources. Absent on a JRE and on minimal vendor images. */
    private static final Path SRC_ZIP = Path.of(System.getProperty("java.home"), "lib", "src.zip");

    /** The slice to parse. Top level only: the subpackages triple the cost for the same signal. */
    private static final String PACKAGE_PATH = "/java.base/java/util";

    // Floors, not targets, and deliberately below the measured value so a JDK upgrade that moves
    // a few classes does not fail the build. Measured on JDK 21: 138 nodes and 158 edges from 135
    // files. A parser that has genuinely broken returns single digits, not 110.
    private static final int MIN_NODES = 110;
    private static final int MIN_EDGES = 110;

    // The stitched sequence graph of SUBSET (issue #276). Measured on JDK 21: 1435 nodes and 1657
    // calls, the same on three runs.
    private static final int MIN_SEQUENCE_NODES = 1200;
    private static final int MIN_SEQUENCE_EDGES = 1400;

    private static List<LogRecord> warnings;
    private static Graph graph;
    private static Path sources;

    /**
     * The core collections, for the slower modes (issue #276). ELK and the symbol-solving sequence
     * parser over all 135 files cost about 19 and 37 seconds on every build; these 24 keep the
     * shapes that matter (deep generics, abstract bases, overloads, inner iterators) for a fraction
     * of that. Collections and Arrays are left out for their size. The full set runs weekly in the
     * third-party corpus instead.
     */
    private static final List<String> SUBSET = List.of(
            "AbstractCollection", "AbstractList", "AbstractMap", "AbstractQueue", "AbstractSequentialList",
            "AbstractSet", "ArrayDeque", "ArrayList", "Collection", "Deque", "HashMap", "HashSet",
            "Iterator", "LinkedHashMap", "LinkedHashSet", "LinkedList", "List", "ListIterator", "Map",
            "NavigableMap", "Queue", "Set", "SortedMap", "TreeMap");
    private static Path subset;

    @BeforeAll
    static void parseTheCorpus(@TempDir Path tempDir) throws IOException {
        if (!Files.exists(SRC_ZIP)) {
            String message = SRC_ZIP + " does not exist, so there is no JDK corpus to parse."
                    + " That is normal on a JRE.";
            if (Boolean.getBoolean("corpus.required")) {
                fail(message + " -Dcorpus.required=true says that is not acceptable here.");
            }
            Assumptions.abort(message);
        }

        sources = tempDir.resolve("corpus");
        Files.createDirectories(sources);
        extractTopLevelSources(sources);
        subset = Files.createDirectories(tempDir.resolve("subset"));
        for (String name : SUBSET) {
            Files.copy(sources.resolve(name + ".java"), subset.resolve(name + ".java"));
        }

        // Parsers report failure by logging rather than by throwing, so the log is the only place
        // a regression surfaces. Count records from the parser packages and nowhere else: the CLI
        // warns about oversized diagrams, and that is advice to a user, not a parse failure.
        warnings = new ArrayList<>();
        Handler collector = parserWarningsInto(warnings);
        Logger root = Logger.getLogger("");
        root.addHandler(collector);
        try {
            graph = new JavaSourceInputParser().parse(sources);
        } finally {
            root.removeHandler(collector);
        }
    }

    private static Handler parserWarningsInto(List<LogRecord> sink) {
        return new Handler() {
            @Override public void publish(LogRecord record) {
                if (record.getLevel().intValue() >= Level.WARNING.intValue()
                        && record.getLoggerName() != null
                        && record.getLoggerName().startsWith("se.deversity.codekarta.input")) {
                    synchronized (sink) {
                        sink.add(record);
                    }
                }
            }

            @Override public void flush() { }

            @Override public void close() { }
        };
    }

    private static void extractTopLevelSources(Path into) throws IOException {
        try (FileSystem zip = FileSystems.newFileSystem(SRC_ZIP, Map.of())) {
            Path pkg = zip.getPath(PACKAGE_PATH);
            assertTrue(Files.isDirectory(pkg), PACKAGE_PATH + " is missing from " + SRC_ZIP);
            try (Stream<Path> entries = Files.list(pkg)) {
                for (Path entry : entries.collect(Collectors.toList())) {
                    String name = entry.getFileName().toString();
                    if (name.endsWith(".java") && Files.isRegularFile(entry)) {
                        Files.copy(entry, into.resolve(name));
                    }
                }
            }
        }
    }

    @Test
    void theParsersSurviveRealJdkSource() {
        assertNotNull(graph, "the corpus did not parse at all");
        assertTrue(graph.getNodes().size() >= MIN_NODES,
                "only " + graph.getNodes().size() + " nodes from " + PACKAGE_PATH
                        + ", expected at least " + MIN_NODES
                        + ". The parsers return a partial graph rather than throwing, so this is"
                        + " what a broken parser looks like.");
        assertTrue(graph.getEdges().size() >= MIN_EDGES,
                "only " + graph.getEdges().size() + " edges, expected at least " + MIN_EDGES);
    }

    @Test
    void nothingInTheCorpusFailedToParse() {
        String detail = warnings.stream()
                .map(LogRecord::getMessage)
                .distinct()
                .limit(10)
                .collect(Collectors.joining("; "));
        assertEquals(0, warnings.size(),
                "the parsers logged " + warnings.size() + " warning(s) on JDK source, which means"
                        + " they silently returned less than they were given: " + detail);
    }

    @Test
    void theRelationshipsJavaUtilHasAlwaysHadAreStillFound() {
        // A node-count floor cannot tell the difference between parsing everything and parsing
        // every declaration but no longer emitting EXTENDS. These facts have held since Java 1.2,
        // so they pin the edge kinds without pinning anything a JDK release is likely to move.
        assertTrue(hasEdge("ArrayList", "AbstractList", "EXTENDS"),
                "ArrayList extends AbstractList, and the parser no longer says so");
        assertTrue(hasEdge("ArrayList", "List", "IMPLEMENTS"),
                "ArrayList implements List, and the parser no longer says so");
        assertTrue(hasNode("HashMap", "CLASS"), "HashMap is missing, or is no longer a CLASS");
        assertTrue(hasNode("Collection", "INTERFACE"),
                "Collection is missing, or is no longer an INTERFACE");
    }

    /**
     * The ELK engine on real source (issue #276): every node gets a position, and the SVG is the
     * same bytes twice. The class mode used to be the only one any corpus exercised.
     */
    @Test
    void theElkLayoutPlacesEveryNodeAndRendersTheSameBytes(@TempDir Path out) throws IOException {
        RunOptions elk = new RunOptions(false, RunOptions.LAYOUT_ELK, false, java.util.Set.of(),
                Integer.MAX_VALUE, false, null, se.deversity.codekarta.input.parser.ClassDiagramParser.DEFAULT_MAX_MEMBERS,
                RunOptions.FORMAT_SVG, false);
        Graph laidOut = new JavaSourceInputParser().parse(subset);

        Path first = KartaCli.run(subset, out.resolve("first"), elk, laidOut);
        Path second = KartaCli.run(subset, out.resolve("second"), elk);

        assertTrue(laidOut.getNodes().stream().allMatch(n -> n.getX() != null && n.getY() != null),
                "ELK left some of java.util's nodes without a position");
        assertEquals(-1L, Files.mismatch(first, second), "two ELK runs over java.util gave different bytes");
    }

    /**
     * The multi-file sequence parser and its symbol solver on real source (issue #276): nothing
     * logged as skipped, and the stitched graph does not collapse.
     */
    @Test
    void theSequenceParserSurvivesRealJdkSource() {
        List<LogRecord> sequenceWarnings = new ArrayList<>();
        Handler collector = parserWarningsInto(sequenceWarnings);
        Logger root = Logger.getLogger("");
        root.addHandler(collector);
        Graph sequence;
        try {
            sequence = new se.deversity.codekarta.input.MultiFileSequenceParser().parse(subset);
        } finally {
            root.removeHandler(collector);
        }

        assertEquals(0, sequenceWarnings.size(), "the sequence parser logged warnings on java.util: "
                + sequenceWarnings.stream().map(LogRecord::getMessage).distinct().limit(5).collect(Collectors.joining("; ")));
        assertTrue(sequence.getNodes().size() >= MIN_SEQUENCE_NODES,
                "only " + sequence.getNodes().size() + " nodes in the stitched sequence graph, expected at least "
                        + MIN_SEQUENCE_NODES);
        assertTrue(sequence.getEdges().size() >= MIN_SEQUENCE_EDGES,
                "only " + sequence.getEdges().size() + " calls, expected at least " + MIN_SEQUENCE_EDGES);
    }

    @Test
    void theSameSourcesRenderTheSameBytes(@TempDir Path out) throws IOException {
        // KartaCli.run is documented as idempotent, and the committed diagrams under docs/diagrams
        // depend on it. Eleven fixture files cannot show a HashMap iteration order leaking into
        // the output. 135 can.
        Path first = KartaCli.run(sources, out.resolve("first"), RunOptions.defaults());
        Path second = KartaCli.run(sources, out.resolve("second"), RunOptions.defaults());

        assertEquals(-1L, Files.mismatch(first, second),
                "two runs over the same 135 files produced different bytes, so something in the"
                        + " pipeline depends on iteration order");
    }

    private static boolean hasNode(String label, String type) {
        return graph.getNodes().stream()
                .anyMatch(n -> label.equals(n.getLabel())
                        && type.equals(String.valueOf(n.getType())));
    }

    private static boolean hasEdge(String from, String to, String type) {
        Map<String, Node> byId = graph.getNodes().stream()
                .collect(Collectors.toMap(Node::getId, n -> n, (a, b) -> a));
        for (Edge edge : graph.getEdges()) {
            Node source = byId.get(edge.getSourceId());
            Node target = byId.get(edge.getTargetId());
            if (source != null && target != null
                    && from.equals(source.getLabel())
                    && to.equals(target.getLabel())
                    && type.equals(String.valueOf(edge.getType()))) {
                return true;
            }
        }
        return false;
    }
}
