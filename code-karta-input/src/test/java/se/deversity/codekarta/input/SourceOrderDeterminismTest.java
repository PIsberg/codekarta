package se.deversity.codekarta.input;

import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Node;
import se.deversity.codekarta.input.parser.ClassDiagramParser;
import se.deversity.codekarta.input.parser.StateMachineParser;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Node order must depend on the source tree, not on the filesystem that holds it.
 *
 * <p>{@code Files.walk} returns entries in a filesystem-defined order: NTFS keeps its directory
 * index sorted by name, ext4 with {@code dir_index} returns them in hash order. Without an
 * explicit sort the same source tree produced one node order on Windows and another on Linux,
 * which reaches the rendered SVG — and downstream repositories commit those files, so the diff
 * arrives as a change nobody made. The parsers sort explicitly; these tests pin that.
 *
 * <p>The class names below are deliberately created in an order unrelated to their alphabetical
 * order, so a parser that preserved creation or hash order would produce a different sequence.
 *
 * <p><b>Where this test discriminates.</b> It was checked against the unfixed parser and passed
 * on NTFS, because Windows already walks in name order: on that filesystem the bug is invisible
 * and so is this test. It fails on a filesystem that returns another order, which is what CI
 * runs and where the defect was found. Treat it as pinning the contract rather than as a
 * regression guard that holds everywhere; the guard that holds everywhere is the
 * "regeneration is idempotent" step in build.yml, which compares real rendered bytes.
 */
class SourceOrderDeterminismTest {

    private static final List<String> CREATION_ORDER = List.of("Zebra", "Alpha", "Mango", "Beta");
    private static final List<String> ALPHABETICAL = List.of("Alpha", "Beta", "Mango", "Zebra");

    private static void writeClasses(Path dir) throws Exception {
        for (String name : CREATION_ORDER) {
            Files.writeString(dir.resolve(name + ".java"), "public class " + name + " { }\n");
        }
    }

    private static List<String> nodeLabelsInOrder(Graph graph) {
        return graph.getNodes().stream().map(Node::getId).toList();
    }

    @Test
    void classDiagramNodesFollowSourceFileOrderRatherThanWalkOrder(@TempDir Path dir) throws Exception {
        writeClasses(dir);

        List<String> ids = nodeLabelsInOrder(new ClassDiagramParser().parse(dir));

        assertEquals(ALPHABETICAL, ids,
                "class nodes must come out in source-file order, not in Files.walk order");
    }

    @Test
    void stateMachineParseOfADirectoryIsOrderStable(@TempDir Path dir) throws Exception {
        writeClasses(dir);

        List<String> first = nodeLabelsInOrder(new StateMachineParser().parse(dir));
        List<String> second = nodeLabelsInOrder(new StateMachineParser().parse(dir));

        assertEquals(first, second, "two parses of one directory must agree on node order");
    }

    @Test
    void repeatedParsesOfTheSameTreeAgree(@TempDir Path dir) throws Exception {
        Path nested = Files.createDirectories(dir.resolve("sub"));
        writeClasses(dir);
        writeClasses(nested);

        List<String> first = nodeLabelsInOrder(new ClassDiagramParser().parse(dir));
        List<String> second = nodeLabelsInOrder(new ClassDiagramParser().parse(dir));

        assertEquals(first, second, "the same tree must parse to the same node order every time");
        assertEquals(first.stream().sorted(Comparator.naturalOrder()).toList(), first,
                "nested directories must not reintroduce walk order");
    }
}
