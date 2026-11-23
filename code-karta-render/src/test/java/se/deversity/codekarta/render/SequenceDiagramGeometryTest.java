package se.deversity.codekarta.render;

import org.junit.jupiter.api.Test;
import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Group;
import se.deversity.codekarta.core.model.Node;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exact geometry of sequence diagrams (issue #273). With the renderer's constants (lane 220,
 * margin 80, header 170 x 58 at y 40, first message at y 130, 54 between messages), lane n's
 * centre is 190 + 220 n.
 */
class SequenceDiagramGeometryTest {

    private final SvgRenderer svg = new SvgRenderer();
    private final SequenceDiagramRenderer renderer = new SequenceDiagramRenderer(svg);

    /** A.run calls B.find (1), then itself through A.helper (2). */
    private static Graph twoLanes() {
        Graph graph = new Graph();
        graph.addNode(new Node("A.run", "METHOD", "run"));
        graph.addNode(new Node("B.find", "METHOD", "find"));
        graph.addNode(new Node("A.helper", "METHOD", "helper"));
        graph.addEdge(call("c1", "A.run", "B.find", "1"));
        graph.addEdge(call("c2", "A.run", "A.helper", "2"));
        return graph;
    }

    @Test
    void lanesLifelinesAndHeadersSitAtTheirLaneCentres() {
        String out = renderer.render(twoLanes(), null);

        assertTrue(out.contains("width=\"960\" height=\"560\""), "small diagrams keep the minimum canvas");
        // lifelines from under the headers (40 + 58) to 130 + 2 * 54 + 50
        assertTrue(out.contains("<line x1=\"190.0\" y1=\"98.0\" x2=\"190.0\" y2=\"288.0\""));
        assertTrue(out.contains("<line x1=\"410.0\" y1=\"98.0\" x2=\"410.0\" y2=\"288.0\""));
        assertTrue(out.contains("<g id=\"A\">\n  <title>A</title>\n  <rect class=\"node-rect\" x=\"105.0\" y=\"40.0\" width=\"170.0\" height=\"58.0\" rx=\"6\" fill=\"url(#grad-CLASS)\" stroke=\"#374151\""), out);
        assertTrue(out.contains("<g id=\"B\">"));
    }

    @Test
    void aCallIsAnArrowToTheEdgeOfTheCalleesActivationBar() {
        String out = renderer.render(twoLanes(), null);

        assertTrue(out.contains("x1=\"190.0\" y1=\"130.0\" x2=\"405.0\" y2=\"130.0\" stroke=\"#92400e\" stroke-width=\"1.6\" marker-end=\"url(#seq-marker-call)\""), out);
        assertTrue(out.contains("x=\"300.0\" y=\"121.0\" text-anchor=\"middle\""), "label centred, 9px above");
        assertTrue(out.contains(">1: find</text>"));
        // activation bars: 10 wide, 26 tall, starting a quarter of their height above the arrow
        assertTrue(out.contains("<rect x=\"405.0\" y=\"123.5\" width=\"10.0\" height=\"26.0\""));
        assertTrue(out.contains("<rect x=\"185.0\" y=\"177.5\" width=\"10.0\" height=\"26.0\""));
    }

    @Test
    void aSelfCallIsALoopToTheRightOfItsLifeline() {
        String out = renderer.render(twoLanes(), null);

        assertTrue(out.contains("d=\"M195.0 184.0 C231.0 179.0 231.0 213.3 195.0 208.3\""), out);
        assertTrue(out.contains("x=\"235.0\" y=\"188.0\" text-anchor=\"start\""));
        assertTrue(out.contains(">2: helper</text>"));
    }

    @Test
    void aCallBackToAnEarlierLanePointsLeft() {
        Graph graph = new Graph();
        graph.addNode(new Node("A.run", "METHOD", "run"));
        graph.addNode(new Node("B.find", "METHOD", "find"));
        graph.addNode(new Node("A.back", "METHOD", "back"));
        graph.addEdge(call("c1", "A.run", "B.find", "1"));
        graph.addEdge(call("c2", "B.find", "A.back", "1"));

        String out = renderer.render(graph, null);

        assertTrue(out.contains("x1=\"410.0\" y1=\"184.0\" x2=\"195.0\" y2=\"184.0\""), out);
    }

    @Test
    void anExceptionIsADashedRedArrowToItsOwnLaneAfterTheCalls() {
        Graph graph = twoLanes();
        graph.addNode(new Node("exception:Boom", "EXCEPTION", "Boom"));
        graph.addEdge(new Edge("x1", "B.find", "exception:Boom", "EXCEPTION_PROPAGATION"));
        graph.addEdge(new Edge("x2", "A.helper", "B.find", "EXCEPTION_PROPAGATION"));

        String out = renderer.render(graph, null);

        assertTrue(out.contains("<g id=\"exception:Boom\">\n  <title>Boom</title>"), "the exception lane is labelled without its prefix");
        assertTrue(out.contains("stroke=\"#dc2626\" stroke-width=\"1.6\" stroke-dasharray=\"6,3\" marker-end=\"url(#seq-marker-exc)\""));
        assertTrue(out.contains(">throws Boom</text>"));
        assertTrue(out.contains(">throws</text>"), "propagation to a caller has no type to name");
        // after the two calls: steps 2 and 3, at 130 + 2 * 54 and 130 + 3 * 54
        assertTrue(out.contains("x1=\"410.0\" y1=\"238.0\" x2=\"625.0\" y2=\"238.0\""), out);
        assertTrue(out.contains("x1=\"190.0\" y1=\"292.0\" x2=\"405.0\" y2=\"292.0\""));
        assertFalse(out.contains("<rect x=\"625.0\""), "an exception gets no activation bar");
    }

    @Test
    void participantsThatNeverSendOrReceiveAreDropped() {
        Graph graph = twoLanes();
        graph.addNode(new Node("Unused", "CLASS", "Unused"));
        graph.addNode(new Node("Api", "INTERFACE", "Api"));

        String out = renderer.render(graph, null);

        assertFalse(out.contains("<g id=\"Unused\">"));
        assertFalse(out.contains("<g id=\"Api\">"));
        List<SequenceDiagramRenderer.Participant> all = renderer.buildParticipants(graph);
        assertEquals(List.of("A", "B", "Unused", "Api"), all.stream().map(SequenceDiagramRenderer.Participant::id).toList());
        assertEquals("INTERFACE", all.get(3).type());
    }

    @Test
    void messagesFollowTheNumericSequenceNotTheTextOne() {
        Graph graph = new Graph();
        graph.addNode(new Node("A.run", "METHOD", "run"));
        for (int i = 1; i <= 10; i++) {
            graph.addNode(new Node("B.m" + i, "METHOD", "m" + i));
        }
        for (int i = 10; i >= 1; i--) {
            graph.addEdge(call("c" + i, "A.run", "B.m" + i, String.valueOf(i)));
        }

        List<SequenceDiagramRenderer.Message> messages = renderer.orderMessages(graph);

        assertEquals("2: m2", messages.get(1).label());
        assertEquals("10: m10", messages.get(9).label());
        String out = renderer.render(graph, null);
        // 130 + 10 * 54 + 50 + 60 + 34
        assertTrue(out.contains("height=\"814\""), "the canvas grows with the number of messages");
    }

    @Test
    void aWideDiagramGrowsPastTheMinimumWidth() {
        Graph graph = new Graph();
        graph.addNode(new Node("A.a", "METHOD", "a"));
        for (String p : List.of("B", "C", "D", "E")) {
            graph.addNode(new Node(p + ".x", "METHOD", "x"));
            graph.addEdge(call("c" + p, "A.a", p + ".x", "1"));
        }

        assertTrue(renderer.render(graph, null).contains("width=\"1260\""), "80 * 2 + 5 * 220");
    }

    @Test
    void anInterfaceParticipantKeepsItsHeaderStyle() {
        Graph graph = twoLanes();
        graph.getNodes().add(0, new Node("B", "INTERFACE", "B"));

        String out = renderer.render(graph, null);

        assertTrue(out.contains("fill=\"url(#grad-INTERFACE)\" stroke=\"#1d4ed8\""), out);
        assertTrue(out.contains(">«interface»</text>"));
    }

    @Test
    void aFrameSpansItsOwnersLanesAndStepsWithATab() {
        Graph graph = twoLanes();
        Group frame = new Group("g", "catch(IOException)");
        frame.addMember("B.find");
        frame.getProperties().put(Group.OWNER_PROPERTY, "A.run");
        graph.addGroup(frame);

        String out = renderer.render(graph, null);

        // lanes 0..1, step 0: x 190 - 85 - 14, y 130 - 18, width 220 + 170 + 28, height 54 + 20
        assertTrue(out.contains("<rect class=\"group-rect\" x=\"91.0\" y=\"112.0\" width=\"418.0\" height=\"74.0\""), out);
        assertTrue(out.contains("<rect x=\"91.0\" y=\"112.0\" width=\"138.0\" height=\"16\""), "tab 18 chars * 7 + 12 wide");
        assertTrue(out.contains("<text x=\"95.0\" y=\"123.0\""));
    }

    @Test
    void aFrameWithNoMatchingMessageIsNotDrawnAndAnUnlabelledOneShowsItsId() {
        Graph graph = twoLanes();
        Group none = new Group("g-none", "never");
        none.addMember("Nowhere.x");
        graph.addGroup(none);
        Group unlabelled = new Group("g-id", null);
        unlabelled.addMember("B.find");
        graph.addGroup(unlabelled);

        String out = renderer.render(graph, null);

        assertFalse(out.contains(">never</text>"));
        assertTrue(out.contains(">g-id</text>"));
    }

    @Test
    void theStylesheetCanBeReplaced() {
        assertTrue(renderer.render(twoLanes(), ".x{}").contains("<style>\n.x{}</style>"));
        assertTrue(renderer.render(twoLanes(), null).contains("<style>\n" + svg.defaultCss() + "</style>"));
    }

    @Test
    void idHelpersSplitOnTheLastDot() {
        assertEquals("a.B", SequenceDiagramRenderer.participantPrefix("a.B.run(String,int)"));
        assertEquals("run(String,int)", SequenceDiagramRenderer.simpleMethodName("a.B.run(String,int)"));
        assertEquals("solo", SequenceDiagramRenderer.participantPrefix("solo"));
        assertEquals("solo", SequenceDiagramRenderer.simpleMethodName("solo"));
        assertEquals(0, SequenceDiagramRenderer.safeInt(null));
        assertEquals(0, SequenceDiagramRenderer.safeInt("x"));
        assertEquals(7, SequenceDiagramRenderer.safeInt(" 7 "));
    }

    private static Edge call(String id, String from, String to, String label) {
        Edge edge = new Edge(id, from, to, "CALLS");
        edge.setLabel(label);
        return edge;
    }
}
