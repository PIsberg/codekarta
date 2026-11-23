package se.deversity.codekarta.render;

import org.junit.jupiter.api.Test;
import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Group;
import se.deversity.codekarta.core.model.Node;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Exact geometry and markup of class-style diagrams. {@code SvgRendererTest} checks that things
 * are drawn; this class checks where and how, because a renderer that executes every line but is
 * only asked whether {@code <svg} appeared let 3 of every 4 mutants live (issue #273). Expected
 * values are worked out from the renderer's own constants (padding 60, minimum canvas 960x560,
 * default box height 86, compartment header 44, line 15, padding 6, attribution band 34).
 */
class SvgRendererGeometryTest {

    private final SvgRenderer renderer = new SvgRenderer();

    // ------------------------------------------------------------------ canvas

    @Test
    void aSmallDiagramGetsTheMinimumCanvasPlusTheAttributionBand() {
        Graph graph = new Graph();
        graph.addNode(placed("A", "CLASS", 10, 10, 160, null));

        // height: max(560, max(500, 10 + 86) + 60 + 0 legend + 34)
        assertTrue(renderer.render(graph).contains("width=\"960\" height=\"594\" viewBox=\"0 0 960 594\""));
    }

    @Test
    void theCanvasGrowsWithTheFarthestNodeAndReservesTheLegend() {
        Graph graph = new Graph();
        graph.addNode(placed("A", "CLASS", 1000, 700, 160, 50.0));
        assertTrue(renderer.render(graph).contains("width=\"1220\" height=\"880\""),
                "1000 + 160 + 60 wide; 700 + 86 + 60 + 34 tall");

        graph.addNode(placed("B", "CLASS", 0, 0, 160, null));
        graph.addEdge(new Edge("e", "A", "B", "EXTENDS"));
        // one legend row: 16 * 2 + 24 + 20 = 76
        assertTrue(renderer.render(graph).contains("width=\"1220\" height=\"956\""));
    }

    @Test
    void theAttributionSitsInTheBottomRightCorner() {
        Graph graph = new Graph();
        graph.addNode(placed("A", "CLASS", 10, 10, 160, null));

        String svg = renderer.render(graph);

        assertTrue(svg.contains("<text x=\"944.0\" y=\"578.0\" text-anchor=\"end\""), "16px in from 960x594");
        assertTrue(svg.contains("href=\"" + SvgRenderer.ATTRIBUTION_URL + "\""));
        assertTrue(svg.endsWith("</svg>\n"));
    }

    // ------------------------------------------------------------------ nodes

    @Test
    void aPlainNodeIsABoxAtItsLayoutPosition() {
        String svg = renderer.renderNode(placed("A", "CLASS", 100, 200, 160, null));

        assertTrue(svg.contains("<g id=\"A\">"));
        assertTrue(svg.contains("<title>A [CLASS]</title>"));
        assertTrue(svg.contains("x=\"100.0\" y=\"200.0\" width=\"160.0\" height=\"86.0\" rx=\"6.0\" fill=\"url(#grad-CLASS)\" stroke=\"#374151\""), svg);
    }

    @Test
    void aTallerLayoutHeightIsKeptAndAShorterOneIsRaisedToTheMinimum() {
        assertTrue(renderer.renderNode(placed("A", "CLASS", 0, 0, 160, 120.0)).contains("height=\"120.0\""));
        assertTrue(renderer.renderNode(placed("A", "CLASS", 0, 0, 160, 40.0)).contains("height=\"86.0\""));
    }

    @Test
    void stateNodesAreRoundedAndUnknownTypesDrawAsClasses() {
        assertTrue(renderer.renderNode(placed("S", "STATE", 0, 0, 160, null)).contains("rx=\"28.0\" fill=\"url(#grad-STATE)\""));
        String odd = renderer.renderNode(placed("X", "WIDGET", 0, 0, 160, null));
        assertTrue(odd.contains("fill=\"url(#grad-CLASS)\" stroke=\"#374151\""), odd);
    }

    @Test
    void jdkModulesAreMutedAndProjectModulesAreNot() {
        Node jdk = placed("java.base", "MODULE", 0, 0, 160, null);
        jdk.setLabel("java.base");
        assertTrue(renderer.renderNode(jdk).contains("fill=\"#f3f4f6\" stroke=\"#9ca3af\""));

        Node app = placed("app", "MODULE", 0, 0, 160, null);
        String svg = renderer.renderNode(app);
        assertTrue(svg.contains("fill=\"url(#grad-MODULE)\" stroke=\"" + SvgRenderer.NODE_STROKE.get("MODULE") + "\""), svg);
        assertTrue(svg.contains(">«mod»</text>"), "a module carries its stereotype");
    }

    @Test
    void anUnplacedNodeIsNotDrawn() {
        assertEquals("", renderer.renderNode(new Node("A", "CLASS", "A")));
    }

    @Test
    void aNodeWithoutALabelShowsItsId() {
        Node node = placed("only.Id", "CLASS", 0, 0, 160, null);
        node.setLabel(null);
        assertTrue(renderer.renderNode(node).contains("<title>only.Id [CLASS]</title>"));
    }

    // ------------------------------------------------------------------ labels

    @Test
    void aQualifiedLabelSplitsIntoAPackageLineAndABoldNameCentredInTheBox() {
        Node node = placed("com.example.Service", "CLASS", 0, 0, 160, null);

        List<String> texts = texts(renderer.renderNode(node));

        // pkg 11 + name (13 + 4) = 28 high, centred in 86: starts at 29
        assertEquals(List.of("80.0|34.5|9.0|com.example", "80.0|48.5|13.0|Service"), texts);
    }

    @Test
    void anInterfaceShowsItsStereotypeAboveTheName() {
        Node node = placed("Api", "INTERFACE", 0, 0, 160, null);

        List<String> texts = texts(renderer.renderNode(node));

        // stereo 13 + name 17 = 30 high, centred in 86: starts at 28, name from 41
        assertEquals(List.of("80.0|34.5|10|«interface»", "80.0|49.5|13.0|Api"), texts);
    }

    @Test
    void aNameTooLongForTheBoxShrinksToTheMinimumFontAndIsEllipsized() {
        String name = "AVeryLongClassNameThatCannotPossiblyFitInsideTheBox";
        Node node = placed(name, "CLASS", 0, 0, 160, null);

        List<String> texts = texts(renderer.renderNode(node));

        // 148px budget at 8pt * 0.62 = 29 characters: 28 kept plus the ellipsis
        assertEquals(List.of("80.0|43.0|8.0|" + name.substring(0, 28) + "…"), texts);
    }

    @Test
    void aLongPackageKeepsItsTrailingSegments() {
        String pkg = "org.example.some.deeply.nested.package.path";
        List<String> texts = texts(renderer.renderNode(placed(pkg + ".Leaf", "CLASS", 0, 0, 160, null)));

        // 148px at 9pt * 0.55 = 29 characters: the ellipsis plus the last 28
        assertEquals("…" + pkg.substring(pkg.length() - 28), texts.get(0).split("\\|")[3]);
    }

    @Test
    void ellipsizingKeepsShortTextAndCollapsesToAnEllipsisWhenNothingFits() {
        assertEquals("ab", SvgRenderer.ellipsizeRight("ab", 10, 20));
        assertEquals("…", SvgRenderer.ellipsizeRight("abc", 10, 15));
        assertEquals("ab…", SvgRenderer.ellipsizeRight("abcdef", 10, 30));
        assertEquals("…", SvgRenderer.ellipsizeLeft("abc", 10, 15));
        assertEquals("…ef", SvgRenderer.ellipsizeLeft("abcdef", 10, 30));
        assertEquals("abc", SvgRenderer.ellipsizeLeft("abc", 10, 30));
        assertEquals("abcd", SvgRenderer.truncate("abcd", 4));
        assertEquals("abc…", SvgRenderer.truncate("abcde", 4));
    }

    // ------------------------------------------------------------------ compartments

    @Test
    void aClassWithMembersGrowsToFitItsCompartments() {
        Node node = placed("Order", "CLASS", 0, 0, 160, null);
        node.setProperties(Map.of("fields", "id: long\nnotes: String", "methods", "submit()"));

        String svg = renderer.renderNode(node);

        // 44 header + (1 + 6 * 2 + 2 * 15) + (1 + 6 * 2 + 15) = 115
        assertTrue(svg.contains("width=\"160.0\" height=\"115.0\""), svg);
        assertTrue(svg.contains("<line x1=\"0.0\" y1=\"44.0\" x2=\"160.0\" y2=\"44.0\""), "separator under the header");
        assertTrue(svg.contains("<text x=\"8.0\" y=\"57.5\""), "first field");
        assertTrue(svg.contains("<text x=\"8.0\" y=\"72.5\""), "second field");
        assertTrue(svg.contains("<line x1=\"0.0\" y1=\"86.0\" x2=\"160.0\" y2=\"86.0\""), "separator before methods");
        assertTrue(svg.contains("<text x=\"8.0\" y=\"99.5\""), "the method");
        assertEquals(115.0, renderer.nodeRenderH(node));
    }

    @Test
    void aShortCompartmentNodeKeepsTheMinimumHeightAndLongLinesAreTruncated() {
        Node node = placed("Tiny", "CLASS", 0, 0, 160, null);
        node.setProperties(Map.of("fields", "", "methods", "aMethodNameLongerThanTheBoxAllows()"));

        String svg = renderer.renderNode(node);

        assertTrue(svg.contains("height=\"86.0\""), "44 + 28 = 72 is raised to 86");
        // (160 - 16) / 6 = 24 characters: 23 kept plus the ellipsis
        assertTrue(svg.contains(">aMethodNameLongerThanTh…</text>"), svg);
        assertFalse(svg.contains("y1=\"86.0\""), "an empty fields section draws no separator of its own");
    }

    @Test
    void membersOnANonClassNodeAreIgnored() {
        Node node = placed("m", "METHOD", 0, 0, 160, null);
        node.setProperties(Map.of("methods", "x()"));
        assertEquals(86.0, renderer.nodeRenderH(node));
    }

    // ------------------------------------------------------------------ escaping

    @Test
    void markupInIdsLabelsAndCaptionsIsEscaped() {
        Graph graph = new Graph();
        Node node = placed("a<b>", "CLASS", 0, 0, 160, null);
        node.setLabel("A<B>&\"C'");
        graph.addNode(node);
        Node other = placed("c", "CLASS", 0, 300, 160, null);
        graph.addNode(other);
        Edge edge = new Edge("e", "a<b>", "c", "HAS");
        edge.setLabel("x<y");
        graph.addEdge(edge);
        Group group = new Group("g", "pkg<&>");
        group.addMember("a<b>");
        graph.addGroup(group);

        String svg = renderer.render(graph);

        assertTrue(svg.contains("<g id=\"a&lt;b&gt;\">"));
        assertTrue(svg.contains("<title>A&lt;B&gt;&amp;&quot;C&apos; [CLASS]</title>"));
        assertTrue(svg.contains("data-source=\"a&lt;b&gt;\""));
        assertTrue(svg.contains(">x&lt;y</text>"));
        assertTrue(svg.contains(">pkg&lt;&amp;&gt;</text>"));
        assertFalse(svg.contains("A<B>"), "no raw markup reaches the SVG");
        assertEquals("", renderer.escapeXml(null));
    }

    // ------------------------------------------------------------------ css

    @Test
    void aCssOverrideReplacesTheDefaultStylesheet() {
        Graph graph = new Graph();
        graph.addNode(placed("A", "CLASS", 0, 0, 160, null));

        String themed = renderer.render(graph, ".node-rect { fill: red; }");
        String plain = renderer.render(graph);

        assertTrue(themed.contains("<style>\n.node-rect { fill: red; }</style>"));
        assertFalse(themed.contains("transition: filter"), "the default rules are replaced, not appended to");
        assertTrue(plain.contains("<style>\n" + renderer.defaultCss() + "</style>"));
        for (String cls : List.of(".node-rect", ".edge-line", ".edge-label", ".group-rect", ".node-label")) {
            assertTrue(renderer.defaultCss().contains(cls), cls + " is a published theming hook");
        }
    }

    // ------------------------------------------------------------------ legend and markers

    @Test
    void theLegendListsThePlacedEdgeTypesInOrderBelowTheDiagram() {
        Graph graph = new Graph();
        graph.addNode(placed("A", "CLASS", 0, 0, 160, null));
        graph.addNode(placed("B", "CLASS", 0, 300, 160, null));
        graph.addNode(new Node("Unplaced", "CLASS", "Unplaced"));
        graph.addEdge(new Edge("e1", "A", "B", "HAS"));
        graph.addEdge(new Edge("e2", "A", "B", "IMPLEMENTS"));
        graph.addEdge(new Edge("e3", "A", "B", "HAS"));
        graph.addEdge(new Edge("e4", "A", "Unplaced", "REQUIRES"));
        graph.addEdge(new Edge("e5", "A", "B", "WEIRD"));

        String svg = renderer.render(graph);

        // bounds 900 x 500 (the minimums); legend 16 * 2 + 2 * 24 + 20 = 100 tall at y 560
        assertTrue(svg.contains("<rect x=\"724.0\" y=\"560.0\" width=\"220.0\" height=\"100.0\""), "legend box");
        assertTrue(svg.contains("<text x=\"740.0\" y=\"580.0\" font-family=\"sans-serif\" font-size=\"11\" font-weight=\"bold\" fill=\"#374151\">Legend</text>"));
        assertTrue(svg.contains("<line x1=\"740.0\" y1=\"604.0\" x2=\"776.0\" y2=\"604.0\" stroke=\"#374151\" stroke-width=\"2\" marker-end=\"url(#marker-open-374151)\"/>"));
        assertTrue(svg.contains("<line x1=\"740.0\" y1=\"628.0\" x2=\"776.0\" y2=\"628.0\" stroke=\"#1d4ed8\" stroke-width=\"2\" stroke-dasharray=\"8,4\" marker-end=\"url(#marker-hollow-1d4ed8)\"/>"));
        assertTrue(svg.contains("<text x=\"784.0\" y=\"608.0\" font-family=\"sans-serif\" font-size=\"11\" fill=\"#374151\">has</text>"));
        assertTrue(svg.contains(">implements</text>"));
        assertFalse(svg.contains(">requires</text>"), "an edge to an unplaced node is not in the legend");
        assertFalse(svg.contains(">weird</text>"), "an unknown edge type is not in the legend");
    }

    @Test
    void eachMarkerIsDefinedOncePerShapeAndColour() {
        Graph graph = new Graph();
        graph.addNode(placed("A", "CLASS", 0, 0, 160, null));
        graph.addNode(placed("B", "CLASS", 0, 300, 160, null));
        graph.addEdge(new Edge("e1", "A", "B", "EXTENDS"));
        graph.addEdge(new Edge("e2", "B", "A", "EXTENDS"));
        graph.addEdge(new Edge("e3", "A", "B", "HAS"));
        graph.addEdge(new Edge("e4", "A", "B", "CALLS"));

        String markers = renderer.buildMarkers(graph);

        assertEquals(3, markers.split("<marker ", -1).length - 1, markers);
        assertTrue(markers.contains("<marker id=\"marker-hollow-374151\" markerWidth=\"12\""));
        assertTrue(markers.contains("<path d=\"M0,0 L10,5 L0,10 Z\" fill=\"#ffffff\" stroke=\"#374151\""));
        assertTrue(markers.contains("<path d=\"M0,0 L8,4 L0,8\" fill=\"none\" stroke=\"#374151\""), "HAS is an open arrow");
        assertTrue(markers.contains("<path d=\"M0,0 L8,4 L0,8 Z\" fill=\"#92400e\"/>"), "CALLS is a filled arrow");
        assertEquals("marker-open-6b7280", SvgRenderer.markerIdFor(null, "#6b7280"));
    }

    // ------------------------------------------------------------------ edges

    @Test
    void aDownwardEdgeRunsFromTheBottomOfTheSourceToTheTopOfTheTarget() {
        Graph graph = twoBoxes(0, 0, 0, 300);

        String svg = renderer.renderEdge(new Edge("e", "A", "B", "EXTENDS"), graph);

        // centres 43 and 343 apart: bow min(40, 300 * 0.18) = 40, to the left of travel
        assertTrue(svg.contains("d=\"M80.0 86.0 Q51.5 193.0 80.0 300.0\""), svg);
        assertTrue(svg.contains("marker-end=\"url(#marker-hollow-374151)\""));
        assertTrue(svg.contains("x=\"65.7\" y=\"187.0\""), "the label sits on the curve, 6px up");
        assertTrue(svg.contains(">extends</text>"), "a relation label replaces the edge label");
    }

    @Test
    void anUpwardEdgeRunsFromTheTopOfTheSourceToTheBottomOfTheTarget() {
        assertTrue(renderer.renderEdge(new Edge("e", "A", "B", "HAS"), twoBoxes(0, 300, 0, 0))
                .contains("d=\"M80.0 300.0 Q108.5 193.0 80.0 86.0\""));
    }

    @Test
    void sidewaysEdgesLeaveFromTheFacingSide() {
        assertTrue(renderer.renderEdge(new Edge("e", "A", "B", "HAS"), twoBoxes(0, 0, 400, 0))
                .contains("d=\"M160.0 43.0 Q280.0 67.0 400.0 43.0\""), "rightwards: 240 / 400 of a 40px bow");
        assertTrue(renderer.renderEdge(new Edge("e", "A", "B", "HAS"), twoBoxes(400, 0, 0, 0))
                .contains("d=\"M400.0 43.0 Q280.0 19.0 160.0 43.0\""), "leftwards");
    }

    @Test
    void aShortEdgeBowsLessAndDashedTypesAreDashed() {
        String svg = renderer.renderEdge(new Edge("e", "A", "B", "IMPLEMENTS"), twoBoxes(0, 0, 0, 100));
        // centres 100 apart: bow 100 * 0.18 = 18, scaled by the 14px gap between the anchors
        assertTrue(svg.contains("d=\"M80.0 86.0 Q77.5 93.0 80.0 100.0\""), svg);
        assertTrue(svg.contains("stroke=\"#1d4ed8\" stroke-width=\"1.6\" stroke-dasharray=\"8,4\""));
    }

    @Test
    void anEdgeWithoutALabelOrRelationNameDrawsNoText() {
        String svg = renderer.renderEdge(new Edge("e", "A", "B", "HAS"), twoBoxes(0, 0, 0, 300));
        assertFalse(svg.contains("<text"), svg);
        String unknown = renderer.renderEdge(new Edge("e", "A", "B", null), twoBoxes(0, 0, 0, 300));
        assertTrue(unknown.contains("stroke=\"#6b7280\""), "an untyped edge is grey");
    }

    @Test
    void anEdgeToAnUnplacedOrMissingNodeIsNotDrawn() {
        Graph graph = twoBoxes(0, 0, 0, 300);
        graph.addNode(new Node("C", "CLASS", "C"));
        assertEquals("", renderer.renderEdge(new Edge("e", "A", "C", "HAS"), graph));
        assertEquals("", renderer.renderEdge(new Edge("e", "A", "Missing", "HAS"), graph));
    }

    // ------------------------------------------------------------------ groups

    @Test
    void aGroupFrameWrapsItsPlacedMembersWith14pxOfPadding() {
        Graph graph = new Graph();
        graph.addNode(placed("A", "CLASS", 100, 100, 160, null));
        graph.addNode(placed("B", "CLASS", 400, 300, 160, null));
        graph.addNode(new Node("Unplaced", "CLASS", "Unplaced"));
        Group group = new Group("g", "pkg");
        group.addMember("A");
        group.addMember("B");
        group.addMember("Unplaced");
        group.addMember("Missing");
        graph.addGroup(group);
        Group empty = new Group("h", "nothing placed");
        empty.addMember("Unplaced");
        graph.addGroup(empty);

        String svg = renderer.render(graph);

        assertTrue(svg.contains("<rect class=\"group-rect\" x=\"86.0\" y=\"86.0\" width=\"488.0\" height=\"314.0\""), svg);
        assertTrue(svg.contains("<text x=\"92.0\" y=\"99.0\" font-family=\"sans-serif\" font-size=\"11\" fill=\"#6b7280\" font-style=\"italic\">pkg</text>"));
        assertFalse(svg.contains(">nothing placed</text>"), "a group with no placed member draws no frame");
    }

    // ------------------------------------------------------------------ routing to the sequence renderer

    @Test
    void onlyMethodsJoinedByIntegerLabelledCallsAreAnInteractionGraph() {
        assertTrue(renderer.isInteractionGraph(calls("1", "2")));
        assertFalse(renderer.isInteractionGraph(calls("1", "first")), "every CALLS label must be an integer");
        assertFalse(renderer.isInteractionGraph(calls("1", null)), "a CALLS edge without a label disqualifies it");
        assertFalse(renderer.isInteractionGraph(calls("one", "two")), "at least one integer label is needed");

        Graph oneMethod = new Graph();
        oneMethod.addNode(new Node("A.a", "METHOD", "a"));
        oneMethod.addNode(new Node("B", "CLASS", "B"));
        oneMethod.addEdge(labelled("e", "A.a", "B", "CALLS", "1"));
        assertFalse(renderer.isInteractionGraph(oneMethod), "two METHOD nodes are needed");

        Graph mostlyStructure = calls("1", "2");
        for (int i = 0; i < 3; i++) {
            mostlyStructure.addEdge(new Edge("h" + i, "A.a", "B.b", "HAS"));
        }
        assertFalse(renderer.isInteractionGraph(mostlyStructure), "most edges must be interactions");

        Graph noEdges = new Graph();
        noEdges.addNode(new Node("A.a", "METHOD", "a"));
        noEdges.addNode(new Node("B.b", "METHOD", "b"));
        assertFalse(renderer.isInteractionGraph(noEdges));
    }

    // ------------------------------------------------------------------ fixtures

    private static Node placed(String id, String type, double x, double y, double w, Double h) {
        Node node = new Node(id, type, id);
        node.setX(x);
        node.setY(y);
        node.setWidth(w);
        node.setHeight(h);
        return node;
    }

    private static Graph twoBoxes(double ax, double ay, double bx, double by) {
        Graph graph = new Graph();
        graph.addNode(placed("A", "CLASS", ax, ay, 160, null));
        graph.addNode(placed("B", "CLASS", bx, by, 160, null));
        return graph;
    }

    private static Graph calls(String first, String second) {
        Graph graph = new Graph();
        graph.addNode(new Node("A.a", "METHOD", "a"));
        graph.addNode(new Node("B.b", "METHOD", "b"));
        graph.addNode(new Node("C.c", "METHOD", "c"));
        graph.addEdge(labelled("e1", "A.a", "B.b", "CALLS", first));
        graph.addEdge(labelled("e2", "B.b", "C.c", "CALLS", second));
        return graph;
    }

    private static Edge labelled(String id, String source, String target, String type, String label) {
        Edge edge = new Edge(id, source, target, type);
        edge.setLabel(label);
        return edge;
    }

    /** Every node-label text as "x|y|font-size|content", in document order. */
    private static List<String> texts(String svg) {
        Matcher m = Pattern.compile("<text class=\"node-label\" x=\"([-0-9.]+)\" y=\"([-0-9.]+)\"[^>]*?font-size=\"([0-9.]+)\"[^>]*>([^<]*)</text>").matcher(svg);
        List<String> found = new ArrayList<>();
        while (m.find()) {
            found.add(m.group(1) + "|" + m.group(2) + "|" + m.group(3) + "|" + m.group(4));
        }
        return found;
    }
}
