package se.deversity.codekarta.render;

import org.junit.jupiter.api.Test;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Node;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Graphviz DOT output (issue #275), for pipelines that already lay graphs out with Graphviz. */
class DotRendererTest {

    private final DotRenderer renderer = new DotRenderer();

    @Test
    void aClassGraphIsADigraphWithPackageClustersAndStyledRelations() {
        assertEquals("""
                digraph karta {
                    rankdir=TB;
                    node [shape=box, style=rounded, fontname="Helvetica"];
                    edge [fontname="Helvetica", fontsize=10];
                    subgraph cluster_0 {
                        label="com.x";
                        n0 [label="Order"];
                        n1 [label="Payable", style="rounded,dashed"];
                        n2 [label="Item"];
                    }
                    n3 [label="Base"];
                    n0 -> n1 [arrowhead=empty, style=dashed];
                    n0 -> n3 [arrowhead=empty];
                    n0 -> n2 [label="items", arrowhead=vee];
                }
                """, renderer.render(MermaidRendererTest.classGraph()));
    }

    @Test
    void callsCarryTheirSequenceNumberAndExceptionsAreDashedRed() {
        String out = renderer.render(MermaidRendererTest.sequenceGraph());
        assertTrue(out.contains("n0 -> n1 [label=\"1\"];"), out);
        assertTrue(out.contains("n1 -> n3 [label=\"throws\", style=dashed, color=\"#dc2626\"];"), out);
        assertTrue(out.contains("n3 [label=\"Boom\", color=\"#dc2626\"];"), out);
    }

    @Test
    void statesAreEllipses() {
        assertTrue(renderer.render(MermaidRendererTest.stateGraph(false)).contains("n0 [label=\"NEW\", shape=ellipse];"));
    }

    @Test
    void quotesBackslashesAndNewlinesAreEscaped() {
        Graph graph = new Graph();
        graph.addNode(new Node("a", "CLASS", "a\"b\\c\nd"));
        assertTrue(renderer.render(graph).contains("n0 [label=\"a\\\"b\\\\c\\nd\"];"), renderer.render(graph));
    }

    @Test
    void theSameGraphGivesTheSameText() {
        assertEquals(renderer.render(MermaidRendererTest.classGraph()), renderer.render(MermaidRendererTest.classGraph()));
    }
}
