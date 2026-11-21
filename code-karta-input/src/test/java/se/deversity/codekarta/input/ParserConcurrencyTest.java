package se.deversity.codekarta.input;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Group;
import se.deversity.codekarta.core.model.Node;
import se.deversity.codekarta.input.parser.CallSequenceParser;
import se.deversity.codekarta.input.parser.ClassDiagramParser;
import se.deversity.codekarta.input.parser.ExceptionFlowParser;
import se.deversity.codekarta.input.parser.ModuleInfoParser;
import se.deversity.codekarta.input.parser.StateMachineParser;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeMap;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Every parser must give the same graph whether it runs alone or beside other parses on other
 * threads. The Maven plugin declares {@code threadSafe = true}, so {@code mvn -T 4} runs several
 * modules through the same loaded classes at once: any parser state shared through a static
 * field shows up here as a thrown exception or a smaller graph (issue #225).
 *
 * <p>Each test computes the serial result first, then runs {@value #RUNS} parses on
 * {@value #THREADS} threads, each with its own parser instance, and asserts that every one of
 * them equals the serial result.
 */
class ParserConcurrencyTest {

    private static final int THREADS = 8;
    private static final int RUNS = 32;
    private static final int FILES = 120;

    @Test
    void classDiagramParserIsSafeToRunConcurrently(@TempDir Path dir) throws Exception {
        for (int i = 0; i < FILES; i++) {
            Files.writeString(dir.resolve("C" + i + ".java"), """
                    package t;
                    public class C%1$d {
                        private C%2$d next;
                        private String name;
                        public int size(int x) { return x; }
                        public void link(C%2$d other) { next = other; }
                    }
                    """.formatted(i, (i + 1) % FILES));
        }

        assertSameAsSerial(() -> new ClassDiagramParser().parse(dir));
    }

    @Test
    void stateMachineParserIsSafeToRunConcurrently(@TempDir Path dir) throws Exception {
        for (int i = 0; i < FILES; i++) {
            Files.writeString(dir.resolve("M" + i + ".java"), """
                    public class M%1$d {
                        enum S%1$d { NEW%1$d, PAID%1$d, SHIPPED%1$d }
                        void pay() { transition(S%1$d.NEW%1$d, S%1$d.PAID%1$d, "pay"); }
                        void ship() { transition(S%1$d.PAID%1$d, S%1$d.SHIPPED%1$d, "ship"); }
                        void transition(Object from, Object to, String event) { }
                    }
                    """.formatted(i));
        }

        assertSameAsSerial(() -> new StateMachineParser().parse(dir));
    }

    @Test
    void moduleInfoParserIsSafeToRunConcurrently(@TempDir Path dir) throws Exception {
        for (int i = 0; i < FILES; i++) {
            Path moduleDir = Files.createDirectories(dir.resolve("m" + i));
            Files.writeString(moduleDir.resolve("module-info.java"), """
                    module m%1$d {
                        requires m%2$d;
                        exports p%1$d;
                    }
                    """.formatted(i, (i + 1) % FILES));
        }

        assertSameAsSerial(() -> new ModuleInfoParser().parseDirectory(dir));
    }

    @Test
    void singleFileParsersAreSafeToRunConcurrently(@TempDir Path dir) throws Exception {
        // CallSequenceParser and ExceptionFlowParser parse through ParserSupport.parseJava21,
        // so one large file parsed from many threads exercises its parser.
        StringBuilder source = new StringBuilder("public class Big {\n");
        for (int i = 0; i < FILES; i++) {
            source.append("""
                        void m%1$d() {
                            try { m%2$d(); } catch (IllegalStateException e) { throw new RuntimeException(e); }
                        }
                    """.formatted(i, (i + 1) % FILES));
        }
        source.append("}\n");
        Path file = dir.resolve("Big.java");
        Files.writeString(file, source);

        assertSameAsSerial(() -> new CallSequenceParser().parse(file));
        assertSameAsSerial(() -> new ExceptionFlowParser().parse(file));
    }

    private static void assertSameAsSerial(Supplier<Graph> parse) throws Exception {
        String serial = fingerprint(parse.get());
        assertFalse(serial.isBlank(), "the serial parse must produce a graph for the comparison to mean anything");

        ExecutorService pool = Executors.newFixedThreadPool(THREADS);
        try {
            List<Callable<String>> tasks = new ArrayList<>();
            for (int i = 0; i < RUNS; i++) {
                tasks.add(() -> fingerprint(parse.get()));
            }
            List<String> failures = new ArrayList<>();
            int run = 0;
            for (Future<String> result : pool.invokeAll(tasks)) {
                try {
                    if (!serial.equals(result.get())) {
                        failures.add("run " + run + ": graph differs from the serial parse");
                    }
                } catch (java.util.concurrent.ExecutionException e) {
                    failures.add("run " + run + ": threw " + e.getCause());
                }
                run++;
            }
            assertEquals(List.of(), failures, failures.size() + " of " + RUNS + " concurrent parses went wrong");
        } finally {
            pool.shutdownNow();
        }
        assertTrue(pool.isShutdown());
    }

    /** Order-sensitive text form of a graph: node, edge and group order reach the rendered SVG. */
    private static String fingerprint(Graph graph) {
        StringBuilder sb = new StringBuilder();
        for (Node n : graph.getNodes()) {
            sb.append("N ").append(n.getId()).append(' ').append(n.getType()).append(' ')
              .append(n.getLabel()).append(' ').append(new TreeMap<>(n.getProperties())).append('\n');
        }
        for (Edge e : graph.getEdges()) {
            sb.append("E ").append(e.getId()).append(' ').append(e.getSourceId()).append("->")
              .append(e.getTargetId()).append(' ').append(e.getType()).append(' ')
              .append(e.getLabel()).append('\n');
        }
        for (Group g : graph.getGroups()) {
            sb.append("G ").append(g.getId()).append(' ').append(g.getMemberIds()).append('\n');
        }
        return sb.toString();
    }
}
