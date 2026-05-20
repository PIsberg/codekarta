package com.karta.render;

import com.karta.core.model.Edge;
import com.karta.core.model.Graph;
import com.karta.core.model.Group;
import com.karta.core.model.Node;
import org.junit.jupiter.api.Test;
import se.deversity.vibetags.annotations.AIParallelTests;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@AIParallelTests
class SequenceDiagramRendererTest {

    private final SvgRenderer renderer = new SvgRenderer();

    // ------------------------------------------------------------------ detection routing

    @Test
    void rendersAsSequenceDiagramWhenDetected() {
        Graph graph = twoMethodGraph();
        String svg = renderer.render(graph);
        assertTrue(svg.contains("<?xml"), "must produce valid SVG");
        // Sequence renderer emits lifelines (vertical dashed lines)
        assertTrue(svg.contains("stroke-dasharray=\"6,4\""), "lifeline dashed line must be present");
    }

    // ------------------------------------------------------------------ participant extraction

    @Test
    void extractsParticipantFromMethodPrefix() {
        List<SequenceDiagramRenderer.Participant> participants =
            new SequenceDiagramRenderer(renderer).buildParticipants(twoMethodGraph());
        assertFalse(participants.isEmpty(), "must derive at least one participant");
        assertTrue(participants.stream().anyMatch(p -> p.id().equals("ServiceA")),
                "ServiceA participant must be derived from ServiceA.doWork");
        assertTrue(participants.stream().anyMatch(p -> p.id().equals("ServiceB")),
                "ServiceB participant must be derived from ServiceB.helper");
    }

    @Test
    void pinExceptionParticipantAtEnd() {
        Graph graph = twoMethodGraph();
        Node exc = new Node("exception:MyException", "EXCEPTION", "MyException");
        graph.addNode(exc);
        Edge propEdge = new Edge("ep1", "ServiceA.doWork", "exception:MyException", "EXCEPTION_PROPAGATION");
        graph.addEdge(propEdge);

        List<SequenceDiagramRenderer.Participant> participants =
            new SequenceDiagramRenderer(renderer).buildParticipants(graph);

        assertEquals("EXCEPTION",
            participants.get(participants.size() - 1).type(),
            "exception participant must be placed last");
    }

    // ------------------------------------------------------------------ message ordering

    @Test
    void ordersMessagesInDfsOrder() {
        Graph graph = twoMethodGraph();
        List<SequenceDiagramRenderer.Message> messages =
            new SequenceDiagramRenderer(renderer).orderMessages(graph);

        assertFalse(messages.isEmpty(), "must produce at least one message");
        // The one CALLS edge must appear as first message
        assertEquals("ServiceA.doWork", messages.get(0).fromId());
        assertEquals("ServiceB.helper", messages.get(0).toId());
    }

    @Test
    void appendsExceptionPropagationAfterCalls() {
        Graph graph = twoMethodGraph();
        Edge exc = new Edge("ep1", "ServiceB.helper", "ServiceA.doWork", "EXCEPTION_PROPAGATION");
        graph.addEdge(exc);

        List<SequenceDiagramRenderer.Message> messages =
            new SequenceDiagramRenderer(renderer).orderMessages(graph);

        assertEquals("CALLS", messages.get(0).type(), "first message must be CALLS");
        assertEquals("EXCEPTION_PROPAGATION", messages.get(messages.size() - 1).type(),
                "EXCEPTION_PROPAGATION must be last");
    }

    // ------------------------------------------------------------------ SVG structure

    @Test
    void rendersParticipantHeaders() {
        Graph graph = twoMethodGraph();
        String svg = renderer.render(graph);
        // Headers carry the node-rect CSS class
        assertTrue(svg.contains("class=\"node-rect\""), "participant header rects must have node-rect class");
        assertTrue(svg.contains("ServiceA"), "ServiceA label must appear");
        assertTrue(svg.contains("ServiceB"), "ServiceB label must appear");
    }

    @Test
    void rendersCallArrow() {
        Graph graph = twoMethodGraph();
        String svg = renderer.render(graph);
        // Message arrow uses edge-line class
        assertTrue(svg.contains("class=\"edge-line\""), "message arrow must carry edge-line class");
        // Amber fill marker for CALLS
        assertTrue(svg.contains("seq-marker-call"), "CALLS marker must be defined");
    }

    @Test
    void rendersClickableAttributionInSequenceDiagrams() {
        Graph graph = twoMethodGraph();
        String svg = renderer.render(graph);

        assertTrue(svg.contains("Created with https://github.com/PIsberg/codekarta"),
                "sequence diagrams must include attribution text");
        assertTrue(svg.contains("href=\"https://github.com/PIsberg/codekarta\""),
                "sequence diagram attribution must link to the project repository");
        assertTrue(svg.contains("text-anchor=\"end\""),
                "sequence diagram attribution must be anchored to the right edge");
        assertTrue(svg.contains("text-decoration=\"underline\""),
                "sequence diagram attribution must be visibly underlined");
        assertTrue(svg.indexOf("Created with https://github.com/PIsberg/codekarta") > svg.indexOf("ServiceA"),
                "attribution must be rendered after sequence content");
    }

    @Test
    void rendersActivationBars() {
        Graph graph = twoMethodGraph();
        String svg = renderer.render(graph);
        // Activation bars are plain rects with amber stroke
        assertTrue(svg.contains("#92400e"), "activation bar must use CALLS/METHOD amber color");
    }

    @Test
    void rendersTryCatchGroupAsFrame() {
        Graph graph = twoMethodGraph();
        Group catchGroup = new Group("catch-boundary", "catch(ServiceException)");
        catchGroup.addMember("ServiceB.helper");
        graph.addGroup(catchGroup);

        String svg = renderer.render(graph);

        assertTrue(svg.contains("group-rect"), "try/catch frame must carry group-rect class");
        assertTrue(svg.contains("catch(ServiceException)"), "try/catch label must appear");
    }

    @Test
    void rendersExceptionArrowWithRedColor() {
        Graph graph = twoMethodGraph();
        Node exc = new Node("exception:Boom", "EXCEPTION", "Boom");
        graph.addNode(exc);
        Edge propEdge = new Edge("ep1", "ServiceB.helper", "exception:Boom", "EXCEPTION_PROPAGATION");
        graph.addEdge(propEdge);

        String svg = renderer.render(graph);

        assertTrue(svg.contains("seq-marker-exc"), "exception marker must be defined");
        // Red color for EXCEPTION_PROPAGATION
        assertTrue(svg.contains("#dc2626"), "exception arrow must use red color");
    }

    // ------------------------------------------------------------------ helpers

    /**
     * Minimal interaction graph: ServiceA.doWork —CALLS(1)→ ServiceB.helper
     */
    private Graph twoMethodGraph() {
        Graph graph = new Graph();
        Node a = new Node("ServiceA.doWork", "METHOD", "doWork");
        a.setX(20.0); a.setY(20.0); a.setWidth(180.0); a.setHeight(70.0);
        Node b = new Node("ServiceB.helper", "METHOD", "helper");
        b.setX(240.0); b.setY(20.0); b.setWidth(180.0); b.setHeight(70.0);
        graph.addNode(a);
        graph.addNode(b);
        Edge e = new Edge("c1", "ServiceA.doWork", "ServiceB.helper", "CALLS");
        e.setLabel("1");
        graph.addEdge(e);
        return graph;
    }
}
