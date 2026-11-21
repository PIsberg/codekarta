package se.deversity.codekarta.layout;

import org.junit.jupiter.api.Test;
import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Group;
import se.deversity.codekarta.core.model.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whatever a layout engine assigns as a node's height, SvgRenderer draws a class box with member
 * compartments at the height its compartments need. Every engine therefore has to reserve that
 * height, or boxes are drawn on top of each other. {@code --layout elk} reserved a fixed 70px for
 * boxes drawn 505px tall (issue #230). These tests are engine-independent and run against both.
 */
class RenderedOverlapTest {

    private static final List<Supplier<LayoutEngine>> ENGINES =
            List.of(SimpleLayoutEngine::new, ElkLayoutEngine::new);

    /** A has a B, B has a C; 14 fields and 14 methods each, plus the HAS field. */
    private static Graph chainOfWideClasses(boolean inPackage) {
        Graph graph = new Graph();
        String[] names = {"A", "B", "C"};
        for (int n = 0; n < names.length; n++) {
            List<String> fields = new ArrayList<>();
            if (n + 1 < names.length) {
                fields.add(names[n + 1].toLowerCase() + ": " + names[n + 1]);
            }
            List<String> methods = new ArrayList<>();
            for (int i = 0; i < 14; i++) {
                fields.add("f" + i + ": int");
                methods.add("m" + i + "(): void");
            }
            Node node = new Node(names[n], "CLASS", names[n]);
            node.setProperties(new java.util.HashMap<>(Map.of(
                    "fields", String.join("\n", fields),
                    "methods", String.join("\n", methods))));
            graph.addNode(node);
        }
        graph.addEdge(new Edge("a-b", "A", "B", "HAS"));
        graph.addEdge(new Edge("b-c", "B", "C", "HAS"));
        if (inPackage) {
            Group pkg = new Group("package-p", "p");
            for (String name : names) {
                pkg.addMember(name);
            }
            graph.addGroup(pkg);
        }
        return graph;
    }

    @Test
    void theEstimateMatchesWhatSvgRendererDraws() {
        Graph graph = chainOfWideClasses(false);

        // Measured from SvgRenderer output in issue #230: A and B 505px, C 490px.
        assertEquals(505.0, RenderedSize.estimateHeight(graph.findNode("A")));
        assertEquals(490.0, RenderedSize.estimateHeight(graph.findNode("C")));
    }

    @Test
    void noTwoClassBoxesOverlapAtTheirRenderedHeight() {
        for (boolean inPackage : new boolean[] {false, true}) {
            for (Supplier<LayoutEngine> engine : ENGINES) {
                Graph graph = engine.get().layout(chainOfWideClasses(inPackage));
                String context = engine.get().getClass().getSimpleName() + (inPackage ? " in a package" : "");

                List<Node> nodes = graph.getNodes();
                for (int i = 0; i < nodes.size(); i++) {
                    for (int j = i + 1; j < nodes.size(); j++) {
                        assertTrue(apart(nodes.get(i), nodes.get(j)),
                                context + ": " + box(nodes.get(i)) + " overlaps " + box(nodes.get(j)));
                    }
                }
            }
        }
    }

    @Test
    void aChainInsideAPackageRunsTopToBottom() {
        for (Supplier<LayoutEngine> engine : ENGINES) {
            Graph graph = engine.get().layout(chainOfWideClasses(true));
            double a = graph.findNode("A").getY();
            double b = graph.findNode("B").getY();
            double c = graph.findNode("C").getY();

            assertTrue(a < b && b < c, engine.get().getClass().getSimpleName()
                    + ": A has B has C should stack downwards, got y = " + a + ", " + b + ", " + c);
        }
    }

    private static boolean apart(Node p, Node q) {
        double ph = RenderedSize.estimateHeight(p);
        double qh = RenderedSize.estimateHeight(q);
        return p.getX() + p.getWidth() <= q.getX() || q.getX() + q.getWidth() <= p.getX()
                || p.getY() + ph <= q.getY() || q.getY() + qh <= p.getY();
    }

    private static String box(Node n) {
        return n.getId() + "[x=" + n.getX() + " y=" + n.getY() + " w=" + n.getWidth()
                + " h=" + RenderedSize.estimateHeight(n) + "]";
    }
}
