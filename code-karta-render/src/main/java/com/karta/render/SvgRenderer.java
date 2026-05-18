package com.karta.render;

import com.karta.core.model.Edge;
import com.karta.core.model.Graph;
import com.karta.core.model.Group;
import com.karta.core.model.Node;
import com.karta.core.model.NodeDimensions;
import se.deversity.vibetags.annotations.AIContext;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@AIContext(
    focus = "CSS class names node-rect, edge-line, group-rect, node-label, edge-label are stable contract points — consumers inject custom themes via the cssString parameter.",
    avoids = "Renaming or removing any CSS class — breaks existing stylesheets."
)
public class SvgRenderer {

    private static final double MIN_WIDTH   = 960.0;
    private static final double MIN_HEIGHT  = 560.0;
    private static final double PADDING     = 60.0;
    private static final double LEGEND_ROW_H = 24.0;
    private static final double LEGEND_PAD  = 16.0;

    // Node fill / stroke / stereotype by type
    private static final Map<String, String> NODE_FILL = Map.of(
        "CLASS",      "#ffffff",
        "INTERFACE",  "#dbeafe",
        "MODULE",     "#ede9fe",
        "METHOD",     "#fef3c7",
        "PACKAGE",    "#d1fae5",
        "EXCEPTION",  "#fee2e2"
    );
    private static final Map<String, String> NODE_STROKE = Map.of(
        "CLASS",      "#374151",
        "INTERFACE",  "#1d4ed8",
        "MODULE",     "#6d28d9",
        "METHOD",     "#92400e",
        "PACKAGE",    "#065f46",
        "EXCEPTION",  "#dc2626"
    );
    private static final Map<String, String> STEREOTYPE = Map.of(
        "INTERFACE",  "«interface»",
        "MODULE",     "«module»",
        "METHOD",     "«method»",
        "PACKAGE",    "«package»",
        "EXCEPTION",  "«exception»"
    );

    // Edge stroke color / dash / relationship label by type
    private static final Map<String, String> EDGE_COLOR = Map.ofEntries(
        Map.entry("EXTENDS",              "#374151"),
        Map.entry("IMPLEMENTS",           "#1d4ed8"),
        Map.entry("CALLS",                "#92400e"),
        Map.entry("HAS",                  "#374151"),
        Map.entry("REQUIRES",             "#6d28d9"),
        Map.entry("EXPORTS",              "#065f46"),
        Map.entry("EXCEPTION_PROPAGATION","#dc2626")
    );
    private static final Map<String, String> EDGE_DASH = Map.of(
        "IMPLEMENTS",           "8,4",
        "EXPORTS",              "4,4",
        "EXCEPTION_PROPAGATION","6,3"
    );
    private static final Map<String, String> EDGE_MARKER = Map.of(
        "EXTENDS",    "url(#hollowTriangle)",
        "IMPLEMENTS", "url(#hollowTriangle)",
        "CALLS",      "url(#filledArrow)",
        "REQUIRES",   "url(#filledArrow)",
        "EXPORTS",    "url(#openArrow)",
        "HAS",        "url(#openArrow)",
        "EXCEPTION_PROPAGATION", "url(#exceptionArrow)"
    );
    private static final Map<String, String> EDGE_RELATION_LABEL = Map.of(
        "EXTENDS",               "extends",
        "IMPLEMENTS",            "implements",
        "HAS",                   "has",
        "REQUIRES",              "requires",
        "EXPORTS",               "exports",
        "EXCEPTION_PROPAGATION", "throws"
    );

    public String render(Graph graph) {
        return render(graph, null);
    }

    public String render(Graph graph, String cssOverride) {
        Set<String> legendTypes = collectLegendTypes(graph);
        double legendH = legendHeight(legendTypes);

        double[] contentBounds = computeBounds(graph);
        double svgWidth  = Math.max(MIN_WIDTH,  contentBounds[0] + PADDING);
        double svgHeight = Math.max(MIN_HEIGHT, contentBounds[1] + PADDING + legendH);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append(String.format(Locale.ROOT, 
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%.0f\" height=\"%.0f\" viewBox=\"0 0 %.0f %.0f\">\n",
            svgWidth, svgHeight, svgWidth, svgHeight));

        // Defs: CSS + arrow markers
        sb.append("<defs>\n");
        sb.append("<style>\n");
        sb.append(cssOverride != null ? cssOverride : defaultCss());
        sb.append("</style>\n");
        sb.append(hollowTriangleMarker());
        sb.append(filledArrowMarker());
        sb.append(openArrowMarker());
        sb.append(exceptionArrowMarker());
        sb.append("</defs>\n");

        // Background
        sb.append(String.format(Locale.ROOT, 
            "<rect width=\"%.0f\" height=\"%.0f\" fill=\"#f9fafb\"/>\n", svgWidth, svgHeight));

        // Groups (background layer)
        for (Group group : graph.getGroups()) {
            sb.append(renderGroup(group, graph));
        }

        // Edges (middle layer)
        for (Edge edge : graph.getEdges()) {
            sb.append(renderEdge(edge, graph));
        }

        // Nodes (foreground layer)
        for (Node node : graph.getNodes()) {
            sb.append(renderNode(node));
        }

        // Legend
        if (!legendTypes.isEmpty()) {
            sb.append(renderLegend(legendTypes, svgWidth, contentBounds[1] + PADDING));
        }

        sb.append("</svg>");
        return sb.toString();
    }

    // ── Node rendering ───────────────────────────────────────────────────────

    private String renderNode(Node node) {
        if (node.getX() == null || node.getY() == null) return "";

        double x = node.getX();
        double y = node.getY();
        double w = node.getWidth()  != null ? node.getWidth()  : NodeDimensions.DEFAULT_WIDTH;
        double h = node.getHeight() != null ? node.getHeight() : NodeDimensions.DEFAULT_HEIGHT;
        String type  = node.getType() != null ? node.getType() : "CLASS";
        String label = escapeXml(node.getLabel() != null ? node.getLabel() : node.getId());

        String fill    = NODE_FILL.getOrDefault(type, "#ffffff");
        String stroke  = NODE_STROKE.getOrDefault(type, "#374151");
        String stereo  = STEREOTYPE.get(type);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "<g id=\"%s\">\n", escapeXml(node.getId())));
        sb.append(String.format(Locale.ROOT, 
            "  <rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" rx=\"6\" " +
            "fill=\"%s\" stroke=\"%s\" stroke-width=\"1.8\"/>\n",
            x, y, w, h, fill, stroke));

        if (stereo != null) {
            // Two-line: stereotype above, label below
            sb.append(String.format(Locale.ROOT, 
                "  <text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" " +
                "font-family=\"sans-serif\" font-size=\"10\" fill=\"%s\" font-style=\"italic\">%s</text>\n",
                x + w / 2, y + h * 0.35, stroke, escapeXml(stereo)));
            sb.append(String.format(Locale.ROOT, 
                "  <text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" dominant-baseline=\"central\" " +
                "font-family=\"sans-serif\" font-size=\"13\" font-weight=\"bold\" fill=\"#1f2937\">%s</text>\n",
                x + w / 2, y + h * 0.65, label));
        } else {
            sb.append(String.format(Locale.ROOT, 
                "  <text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" dominant-baseline=\"central\" " +
                "font-family=\"sans-serif\" font-size=\"13\" font-weight=\"bold\" fill=\"#1f2937\">%s</text>\n",
                x + w / 2, y + h / 2, label));
        }
        sb.append("</g>\n");
        return sb.toString();
    }

    // ── Edge rendering ───────────────────────────────────────────────────────

    private String renderEdge(Edge edge, Graph graph) {
        Node source = graph.findNode(edge.getSourceId());
        Node target = graph.findNode(edge.getTargetId());
        if (source == null || target == null
                || source.getX() == null || target.getX() == null) {
            return "";
        }

        double sw = source.getWidth()  != null ? source.getWidth()  : NodeDimensions.DEFAULT_WIDTH;
        double sh = source.getHeight() != null ? source.getHeight() : NodeDimensions.DEFAULT_HEIGHT;
        double tw = target.getWidth()  != null ? target.getWidth()  : NodeDimensions.DEFAULT_WIDTH;
        double th = target.getHeight() != null ? target.getHeight() : NodeDimensions.DEFAULT_HEIGHT;

        // Smart attachment: prefer vertical (top/bottom) when nodes are stacked
        double sx, sy, tx, ty;
        double srcCX = source.getX() + sw / 2;
        double srcCY = source.getY() + sh / 2;
        double tgtCX = target.getX() + tw / 2;
        double tgtCY = target.getY() + th / 2;

        double dy = tgtCY - srcCY;
        double dx = tgtCX - srcCX;

        if (Math.abs(dy) >= Math.abs(dx)) {
            // vertical dominant: bottom→top or top→bottom
            if (dy > 0) {
                sx = srcCX; sy = source.getY() + sh;
                tx = tgtCX; ty = target.getY();
            } else {
                sx = srcCX; sy = source.getY();
                tx = tgtCX; ty = target.getY() + th;
            }
        } else {
            // horizontal dominant: right→left or left→right
            if (dx > 0) {
                sx = source.getX() + sw; sy = srcCY;
                tx = target.getX();      ty = tgtCY;
            } else {
                sx = source.getX();      sy = srcCY;
                tx = target.getX() + tw; ty = tgtCY;
            }
        }

        String type   = edge.getType() != null ? edge.getType() : "";
        String color  = EDGE_COLOR.getOrDefault(type, "#6b7280");
        String dash   = EDGE_DASH.get(type);
        String marker = EDGE_MARKER.getOrDefault(type, "url(#openArrow)");

        StringBuilder sb = new StringBuilder();
        String dashAttr = dash != null ? String.format(Locale.ROOT, " stroke-dasharray=\"%s\"", dash) : "";
        sb.append(String.format(Locale.ROOT, 
            "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" " +
            "stroke=\"%s\" stroke-width=\"1.6\"%s marker-end=\"%s\"/>\n",
            sx, sy, tx, ty, color, dashAttr, marker));

        // Relationship label (e.g. "extends") — use explicit EDGE_RELATION_LABEL, fall back to edge.getLabel()
        String relLabel = EDGE_RELATION_LABEL.getOrDefault(type,
                          edge.getLabel() != null ? edge.getLabel() : null);
        if (relLabel != null) {
            double mx = (sx + tx) / 2;
            double my = (sy + ty) / 2 - 6;
            sb.append(String.format(Locale.ROOT, 
                "<text x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" " +
                "font-family=\"sans-serif\" font-size=\"10\" fill=\"%s\" " +
                "font-style=\"italic\">%s</text>\n",
                mx, my, color, escapeXml(relLabel)));
        }

        return sb.toString();
    }

    // ── Group rendering ──────────────────────────────────────────────────────

    private String renderGroup(Group group, Graph graph) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        boolean any = false;

        for (String memberId : group.getMemberIds()) {
            Node node = graph.findNode(memberId);
            if (node == null || node.getX() == null) continue;
            double w = node.getWidth()  != null ? node.getWidth()  : NodeDimensions.DEFAULT_WIDTH;
            double h = node.getHeight() != null ? node.getHeight() : NodeDimensions.DEFAULT_HEIGHT;
            minX = Math.min(minX, node.getX());
            minY = Math.min(minY, node.getY());
            maxX = Math.max(maxX, node.getX() + w);
            maxY = Math.max(maxY, node.getY() + h);
            any = true;
        }
        if (!any) return "";

        double pad = 14;
        return String.format(Locale.ROOT, 
            "<rect class=\"group-rect\" x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" rx=\"8\" " +
            "fill=\"none\" stroke=\"#9ca3af\" stroke-dasharray=\"6,4\" stroke-width=\"1.2\"/>\n" +
            "<text x=\"%.1f\" y=\"%.1f\" font-family=\"sans-serif\" font-size=\"11\" " +
            "fill=\"#6b7280\" font-style=\"italic\">%s</text>\n",
            minX - pad, minY - pad, (maxX - minX + 2 * pad), (maxY - minY + 2 * pad),
            minX - pad + 6, minY - pad + 13,
            escapeXml(group.getLabel() != null ? group.getLabel() : group.getId()));
    }

    // ── Legend ───────────────────────────────────────────────────────────────

    private Set<String> collectLegendTypes(Graph graph) {
        Set<String> types = new LinkedHashSet<>();
        for (Edge e : graph.getEdges()) {
            if (e.getType() == null || !EDGE_COLOR.containsKey(e.getType())) continue;
            Node src = graph.findNode(e.getSourceId());
            Node tgt = graph.findNode(e.getTargetId());
            if (src != null && tgt != null && src.getX() != null && tgt.getX() != null) {
                types.add(e.getType());
            }
        }
        return types;
    }

    private double legendHeight(Set<String> types) {
        if (types.isEmpty()) return 0;
        return LEGEND_PAD * 2 + types.size() * LEGEND_ROW_H + 20;
    }

    private String renderLegend(Set<String> types, double svgWidth, double top) {
        double boxW = 220;
        double boxH = legendHeight(types);
        double bx = svgWidth - boxW - LEGEND_PAD;
        double by = top;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, 
            "<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" rx=\"6\" " +
            "fill=\"#ffffff\" stroke=\"#d1d5db\" stroke-width=\"1\"/>\n",
            bx, by, boxW, boxH));
        sb.append(String.format(Locale.ROOT, 
            "<text x=\"%.1f\" y=\"%.1f\" font-family=\"sans-serif\" font-size=\"11\" " +
            "font-weight=\"bold\" fill=\"#374151\">Legend</text>\n",
            bx + LEGEND_PAD, by + LEGEND_PAD + 4));

        int i = 0;
        for (String type : types) {
            double ry = by + LEGEND_PAD + 20 + i * LEGEND_ROW_H;
            String color = EDGE_COLOR.getOrDefault(type, "#6b7280");
            String dash  = EDGE_DASH.get(type);
            String dashAttr = dash != null ? String.format(Locale.ROOT, " stroke-dasharray=\"%s\"", dash) : "";
            sb.append(String.format(Locale.ROOT, 
                "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" " +
                "stroke=\"%s\" stroke-width=\"2\"%s marker-end=\"%s\"/>\n",
                bx + LEGEND_PAD, ry + 8, bx + LEGEND_PAD + 36, ry + 8,
                color, dashAttr, EDGE_MARKER.getOrDefault(type, "url(#openArrow)")));
            sb.append(String.format(Locale.ROOT, 
                "<text x=\"%.1f\" y=\"%.1f\" font-family=\"sans-serif\" font-size=\"11\" " +
                "fill=\"#374151\">%s</text>\n",
                bx + LEGEND_PAD + 44, ry + 12, escapeXml(type.toLowerCase().replace('_', ' '))));
            i++;
        }
        return sb.toString();
    }

    // ── Marker defs ──────────────────────────────────────────────────────────

    private String hollowTriangleMarker() {
        return "<marker id=\"hollowTriangle\" markerWidth=\"12\" markerHeight=\"12\" " +
               "refX=\"10\" refY=\"5\" orient=\"auto\">\n" +
               "  <path d=\"M0,0 L10,5 L0,10 Z\" fill=\"#ffffff\" stroke=\"#374151\" stroke-width=\"1\"/>\n" +
               "</marker>\n";
    }

    private String filledArrowMarker() {
        return "<marker id=\"filledArrow\" markerWidth=\"10\" markerHeight=\"8\" " +
               "refX=\"8\" refY=\"4\" orient=\"auto\">\n" +
               "  <path d=\"M0,0 L8,4 L0,8 Z\" fill=\"#374151\"/>\n" +
               "</marker>\n";
    }

    private String openArrowMarker() {
        return "<marker id=\"openArrow\" markerWidth=\"10\" markerHeight=\"8\" " +
               "refX=\"8\" refY=\"4\" orient=\"auto\">\n" +
               "  <path d=\"M0,0 L8,4 L0,8\" fill=\"none\" stroke=\"#6b7280\" stroke-width=\"1.2\"/>\n" +
               "</marker>\n";
    }

    private String exceptionArrowMarker() {
        return "<marker id=\"exceptionArrow\" markerWidth=\"10\" markerHeight=\"8\" " +
               "refX=\"8\" refY=\"4\" orient=\"auto\">\n" +
               "  <path d=\"M0,0 L8,4 L0,8 Z\" fill=\"#dc2626\"/>\n" +
               "</marker>\n";
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private double[] computeBounds(Graph graph) {
        double maxX = MIN_WIDTH  - PADDING;
        double maxY = MIN_HEIGHT - PADDING;
        for (Node node : graph.getNodes()) {
            if (node.getX() == null) continue;
            double w = node.getWidth()  != null ? node.getWidth()  : NodeDimensions.DEFAULT_WIDTH;
            double h = node.getHeight() != null ? node.getHeight() : NodeDimensions.DEFAULT_HEIGHT;
            maxX = Math.max(maxX, node.getX() + w);
            maxY = Math.max(maxY, node.getY() + h);
        }
        return new double[]{maxX, maxY};
    }

    private String defaultCss() {
        return "svg { font-family: sans-serif; }\n";
    }

    private String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
