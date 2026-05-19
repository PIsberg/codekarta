package com.karta.render;

import com.karta.core.model.Edge;
import com.karta.core.model.Graph;
import com.karta.core.model.Group;
import com.karta.core.model.Node;
import com.karta.core.model.NodeDimensions;
import se.deversity.vibetags.annotations.AIContext;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@AIContext(
    focus = "CSS class names node-rect, edge-line, group-rect, node-label, edge-label are stable contract points — consumers inject custom themes via the cssString param.",
    avoids = "Renaming or removing any CSS class — breaks existing stylesheets."
)
public class SvgRenderer {

    private static final double MIN_WIDTH    = 960.0;
    private static final double MIN_HEIGHT   = 560.0;
    private static final double PADDING      = 60.0;
    private static final double LEGEND_ROW_H = 24.0;
    private static final double LEGEND_PAD   = 16.0;

    // Renderer-local sizing — layout engines use NodeDimensions constants (locked @AILocked)
    static final double RENDER_NODE_W        = NodeDimensions.DEFAULT_WIDTH;
    static final double RENDER_NODE_H        = 86.0;
    static final double COMPARTMENT_HEADER_H = 44.0;
    static final double COMPARTMENT_LINE_H   = 15.0;
    static final double COMPARTMENT_PADDING  = 6.0;

    // Package-private so SequenceDiagramRenderer can reuse the same palette
    static final Map<String, String> NODE_FILL = Map.of(
        "INTERFACE", "#dbeafe",
        "MODULE",    "#ede9fe",
        "METHOD",    "#fef3c7",
        "PACKAGE",   "#d1fae5",
        "EXCEPTION", "#fee2e2"
    );
    static final Map<String, String> NODE_STROKE = Map.of(
        "CLASS",     "#374151",
        "INTERFACE", "#1d4ed8",
        "MODULE",    "#6d28d9",
        "METHOD",    "#92400e",
        "PACKAGE",   "#065f46",
        "EXCEPTION", "#dc2626"
    );
    static final Map<String, String> STEREOTYPE = Map.of(
        "INTERFACE", "«interface»",
        "MODULE",    "«mod»",
        "METHOD",    "«method»",
        "PACKAGE",   "«pkg»",
        "EXCEPTION", "«exception»"
    );
    static final Map<String, String> EDGE_COLOR = Map.ofEntries(
        Map.entry("EXTENDS",               "#374151"),
        Map.entry("IMPLEMENTS",            "#1d4ed8"),
        Map.entry("CALLS",                 "#92400e"),
        Map.entry("HAS",                   "#374151"),
        Map.entry("REQUIRES",              "#6d28d9"),
        Map.entry("EXPORTS",               "#065f46"),
        Map.entry("EXCEPTION_PROPAGATION", "#dc2626")
    );
    static final Map<String, String> EDGE_DASH = Map.of(
        "IMPLEMENTS",            "8,4",
        "EXPORTS",               "4,4",
        "EXCEPTION_PROPAGATION", "6,3"
    );
    static final Map<String, String> MARKER_SHAPE = Map.ofEntries(
        Map.entry("EXTENDS",               "hollow"),
        Map.entry("IMPLEMENTS",            "hollow"),
        Map.entry("CALLS",                 "filled"),
        Map.entry("REQUIRES",              "filled"),
        Map.entry("EXPORTS",               "open"),
        Map.entry("HAS",                   "open"),
        Map.entry("EXCEPTION_PROPAGATION", "filled")
    );
    private static final Map<String, String> EDGE_RELATION_LABEL = Map.of(
        "EXTENDS",               "extends",
        "IMPLEMENTS",            "implements",
        "REQUIRES",              "requires",
        "EXPORTS",               "exports",
        "EXCEPTION_PROPAGATION", "throws"
    );

    // ------------------------------------------------------------------ public API

    public String render(Graph graph) {
        return render(graph, null);
    }

    public String render(Graph graph, String cssOverride) {
        if (isInteractionGraph(graph)) {
            return new SequenceDiagramRenderer(this).render(graph, cssOverride);
        }

        Set<String> legendTypes = collectLegendTypes(graph);
        double      legendH     = legendHeight(legendTypes);
        double[]    bounds      = computeBounds(graph);
        double      svgW        = Math.max(MIN_WIDTH,  bounds[0] + PADDING);
        double      svgH        = Math.max(MIN_HEIGHT, bounds[1] + PADDING + legendH);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append(String.format(Locale.ROOT,
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%.0f\" height=\"%.0f\" " +
            "viewBox=\"0 0 %.0f %.0f\">\n", svgW, svgH, svgW, svgH));

        sb.append("<defs>\n");
        sb.append("<style>\n").append(cssOverride != null ? cssOverride : defaultCss()).append("</style>\n");
        sb.append(dropShadowFilter());
        sb.append(buildMarkers(graph));
        sb.append("</defs>\n");

        sb.append(String.format(Locale.ROOT,
            "<rect width=\"%.0f\" height=\"%.0f\" fill=\"#f9fafb\"/>\n", svgW, svgH));

        for (Group group : graph.getGroups()) sb.append(renderGroup(group, graph));
        for (Edge  edge  : graph.getEdges())  sb.append(renderEdge(edge, graph));
        for (Node  node  : graph.getNodes())  sb.append(renderNode(node));

        if (!legendTypes.isEmpty()) sb.append(renderLegend(legendTypes, svgW, bounds[1] + PADDING));
        sb.append("</svg>");
        return sb.toString();
    }

    // ------------------------------------------------------------------ node

    String renderNode(Node node) {
        if (node.getX() == null || node.getY() == null) return "";
        double x  = node.getX();
        double y  = node.getY();
        double w  = node.getWidth() != null ? node.getWidth() : RENDER_NODE_W;
        String type   = node.getType() != null ? node.getType() : "CLASS";
        String label  = escapeXml(node.getLabel() != null ? node.getLabel() : node.getId());
        String fill   = NODE_FILL.getOrDefault(type, "#ffffff");
        String stroke = NODE_STROKE.getOrDefault(type, "#374151");
        String stereo = STEREOTYPE.get(type);

        Map<String, String> props = node.getProperties();
        boolean compartments = props != null
                && (props.containsKey("fields") || props.containsKey("methods"))
                && ("CLASS".equals(type) || "INTERFACE".equals(type));
        double h = compartments ? computeCompartmentHeight(props)
                 : node.getHeight() != null ? Math.max(node.getHeight(), RENDER_NODE_H) : RENDER_NODE_H;

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT, "<g id=\"%s\">\n", escapeXml(node.getId())));
        sb.append("  <title>").append(label).append(" [").append(type).append("]</title>\n");
        sb.append(String.format(Locale.ROOT,
            "  <rect class=\"node-rect\" x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" " +
            "rx=\"6\" fill=\"%s\" stroke=\"%s\" stroke-width=\"1.8\" filter=\"url(#nodeShadow)\"/>\n",
            x, y, w, h, fill, stroke));

        if (compartments) {
            appendCompartments(sb, x, y, w, label, stereo, stroke, props);
        } else if (stereo != null) {
            sb.append(String.format(Locale.ROOT,
                "  <text class=\"node-label\" x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" " +
                "font-family=\"sans-serif\" font-size=\"10\" fill=\"%s\" " +
                "font-style=\"italic\">%s</text>\n",
                x + w / 2, y + h * 0.35, stroke, escapeXml(stereo)));
            sb.append(String.format(Locale.ROOT,
                "  <text class=\"node-label\" x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" " +
                "dominant-baseline=\"central\" font-family=\"sans-serif\" font-size=\"13\" " +
                "font-weight=\"bold\" fill=\"#1f2937\">%s</text>\n",
                x + w / 2, y + h * 0.65, label));
        } else {
            sb.append(String.format(Locale.ROOT,
                "  <text class=\"node-label\" x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" " +
                "dominant-baseline=\"central\" font-family=\"sans-serif\" font-size=\"13\" " +
                "font-weight=\"bold\" fill=\"#1f2937\">%s</text>\n",
                x + w / 2, y + h / 2, label));
        }
        sb.append("</g>\n");
        return sb.toString();
    }

    private void appendCompartments(StringBuilder sb, double x, double y, double w,
                                     String label, String stereo, String stroke,
                                     Map<String, String> props) {
        double headerH = COMPARTMENT_HEADER_H;
        if (stereo != null) {
            sb.append(String.format(Locale.ROOT,
                "  <text class=\"node-label\" x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" " +
                "font-family=\"sans-serif\" font-size=\"10\" fill=\"%s\" " +
                "font-style=\"italic\">%s</text>\n",
                x + w / 2, y + headerH * 0.38, stroke, escapeXml(stereo)));
            sb.append(String.format(Locale.ROOT,
                "  <text class=\"node-label\" x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" " +
                "dominant-baseline=\"central\" font-family=\"sans-serif\" font-size=\"13\" " +
                "font-weight=\"bold\" fill=\"#1f2937\">%s</text>\n",
                x + w / 2, y + headerH * 0.72, label));
        } else {
            sb.append(String.format(Locale.ROOT,
                "  <text class=\"node-label\" x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" " +
                "dominant-baseline=\"central\" font-family=\"sans-serif\" font-size=\"13\" " +
                "font-weight=\"bold\" fill=\"#1f2937\">%s</text>\n",
                x + w / 2, y + headerH / 2, label));
        }
        double curY = y + headerH;
        for (String section : new String[]{"fields", "methods"}) {
            String text = props.get(section);
            if (text == null || text.isEmpty()) continue;
            sb.append(String.format(Locale.ROOT,
                "  <line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" " +
                "stroke=\"%s\" stroke-width=\"0.8\" opacity=\"0.4\"/>\n",
                x, curY, x + w, curY, stroke));
            curY += COMPARTMENT_PADDING;
            for (String line : text.split("\n")) {
                sb.append(String.format(Locale.ROOT,
                    "  <text x=\"%.1f\" y=\"%.1f\" font-family=\"sans-serif\" font-size=\"10\" " +
                    "fill=\"#374151\" dominant-baseline=\"central\">%s</text>\n",
                    x + 8, curY + COMPARTMENT_LINE_H / 2, escapeXml(truncate(line, 28))));
                curY += COMPARTMENT_LINE_H;
            }
            curY += COMPARTMENT_PADDING;
        }
    }

    double computeCompartmentHeight(Map<String, String> props) {
        double h = COMPARTMENT_HEADER_H;
        for (String key : new String[]{"fields", "methods"}) {
            String text = props.get(key);
            if (text != null && !text.isEmpty()) {
                h += 1.0 + COMPARTMENT_PADDING * 2 + text.split("\n").length * COMPARTMENT_LINE_H;
            }
        }
        return Math.max(h, RENDER_NODE_H);
    }

    // ------------------------------------------------------------------ edge

    String renderEdge(Edge edge, Graph graph) {
        Node src    = graph.findNode(edge.getSourceId());
        Node target = graph.findNode(edge.getTargetId());
        if (src == null || target == null || src.getX() == null || target.getX() == null) return "";

        double sw = src.getWidth()    != null ? src.getWidth()    : RENDER_NODE_W;
        double sh = nodeRenderH(src);
        double tw = target.getWidth() != null ? target.getWidth() : RENDER_NODE_W;
        double th = nodeRenderH(target);

        double srcCX = src.getX() + sw / 2, srcCY = src.getY() + sh / 2;
        double tgtCX = target.getX() + tw / 2, tgtCY = target.getY() + th / 2;
        double dx = tgtCX - srcCX, dy = tgtCY - srcCY;

        double sx, sy, tx, ty;
        if (Math.abs(dy) >= Math.abs(dx)) {
            if (dy > 0) { sx = srcCX; sy = src.getY() + sh; tx = tgtCX; ty = target.getY(); }
            else         { sx = srcCX; sy = src.getY();      tx = tgtCX; ty = target.getY() + th; }
        } else {
            if (dx > 0) { sx = src.getX() + sw; sy = srcCY; tx = target.getX();      ty = tgtCY; }
            else         { sx = src.getX();      sy = srcCY; tx = target.getX() + tw; ty = tgtCY; }
        }

        String type     = edge.getType() != null ? edge.getType() : "";
        String color    = EDGE_COLOR.getOrDefault(type, "#6b7280");
        String dash     = EDGE_DASH.get(type);
        String markerId = markerIdFor(type, color);

        // Perpendicular bow — reduces crossing confusion vs straight lines
        double mx  = (sx + tx) / 2, my = (sy + ty) / 2;
        double len = Math.max(1.0, Math.sqrt(dx * dx + dy * dy));
        double bow = Math.min(40.0, len * 0.18);
        double cx  = mx - (ty - sy) / len * bow;
        double cy  = my + (tx - sx) / len * bow;

        StringBuilder sb = new StringBuilder();
        String dashAttr = dash != null ? String.format(Locale.ROOT, " stroke-dasharray=\"%s\"", dash) : "";
        sb.append(String.format(Locale.ROOT,
            "<path class=\"edge-line\" d=\"M%.1f %.1f Q%.1f %.1f %.1f %.1f\" " +
            "fill=\"none\" stroke=\"%s\" stroke-width=\"1.6\"%s marker-end=\"url(#%s)\"/>\n",
            sx, sy, cx, cy, tx, ty, color, dashAttr, markerId));

        String relLabel = EDGE_RELATION_LABEL.getOrDefault(type,
                          edge.getLabel() != null ? edge.getLabel() : null);
        if (relLabel != null) {
            double lx = (sx + 2 * cx + tx) / 4;
            double ly = (sy + 2 * cy + ty) / 4 - 6;
            sb.append(String.format(Locale.ROOT,
                "<text class=\"edge-label\" x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" " +
                "font-family=\"sans-serif\" font-size=\"10\" fill=\"%s\" " +
                "font-style=\"italic\">%s</text>\n",
                lx, ly, color, escapeXml(relLabel)));
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ group

    private String renderGroup(Group group, Graph graph) {
        double minX = Double.MAX_VALUE, minY = Double.MAX_VALUE;
        double maxX = -Double.MAX_VALUE, maxY = -Double.MAX_VALUE;
        boolean any = false;
        for (String memberId : group.getMemberIds()) {
            Node node = graph.findNode(memberId);
            if (node == null || node.getX() == null) continue;
            double w = node.getWidth() != null ? node.getWidth() : RENDER_NODE_W;
            double h = nodeRenderH(node);
            minX = Math.min(minX, node.getX());
            minY = Math.min(minY, node.getY());
            maxX = Math.max(maxX, node.getX() + w);
            maxY = Math.max(maxY, node.getY() + h);
            any  = true;
        }
        if (!any) return "";
        double pad = 14;
        return String.format(Locale.ROOT,
            "<rect class=\"group-rect\" x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" " +
            "rx=\"8\" fill=\"#f3f4f6\" fill-opacity=\"0.5\" stroke=\"#9ca3af\" " +
            "stroke-dasharray=\"6,4\" stroke-width=\"1.2\"/>\n" +
            "<text x=\"%.1f\" y=\"%.1f\" font-family=\"sans-serif\" font-size=\"11\" " +
            "fill=\"#6b7280\" font-style=\"italic\">%s</text>\n",
            minX - pad, minY - pad, maxX - minX + 2 * pad, maxY - minY + 2 * pad,
            minX - pad + 6, minY - pad + 13,
            escapeXml(group.getLabel() != null ? group.getLabel() : group.getId()));
    }

    // ------------------------------------------------------------------ markers

    String buildMarkers(Graph graph) {
        Map<String, String> defs = new LinkedHashMap<>();
        for (Edge edge : graph.getEdges()) {
            String type  = edge.getType() != null ? edge.getType() : "";
            String color = EDGE_COLOR.getOrDefault(type, "#6b7280");
            String id    = markerIdFor(type, color);
            defs.computeIfAbsent(id, k -> buildMarkerSvg(id,
                    MARKER_SHAPE.getOrDefault(type, "open"), color));
        }
        StringBuilder sb = new StringBuilder();
        defs.values().forEach(sb::append);
        return sb.toString();
    }

    static String markerIdFor(String edgeType, String color) {
        String shape = MARKER_SHAPE.getOrDefault(edgeType != null ? edgeType : "", "open");
        return "marker-" + shape + "-" + color.replace("#", "");
    }

    static String buildMarkerSvg(String id, String shape, String color) {
        return switch (shape) {
            case "hollow" -> String.format(Locale.ROOT,
                "<marker id=\"%s\" markerWidth=\"12\" markerHeight=\"12\" " +
                "refX=\"10\" refY=\"5\" orient=\"auto\">\n" +
                "  <path d=\"M0,0 L10,5 L0,10 Z\" fill=\"#ffffff\" stroke=\"%s\" stroke-width=\"1\"/>\n" +
                "</marker>\n", id, color);
            case "filled" -> String.format(Locale.ROOT,
                "<marker id=\"%s\" markerWidth=\"10\" markerHeight=\"8\" " +
                "refX=\"8\" refY=\"4\" orient=\"auto\">\n" +
                "  <path d=\"M0,0 L8,4 L0,8 Z\" fill=\"%s\"/>\n" +
                "</marker>\n", id, color);
            default -> String.format(Locale.ROOT,
                "<marker id=\"%s\" markerWidth=\"10\" markerHeight=\"8\" " +
                "refX=\"8\" refY=\"4\" orient=\"auto\">\n" +
                "  <path d=\"M0,0 L8,4 L0,8\" fill=\"none\" stroke=\"%s\" stroke-width=\"1.2\"/>\n" +
                "</marker>\n", id, color);
        };
    }

    // ------------------------------------------------------------------ legend

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
        double boxW = 220, boxH = legendHeight(types);
        double bx = svgWidth - boxW - LEGEND_PAD, by = top;
        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
            "<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" rx=\"6\" " +
            "fill=\"#ffffff\" stroke=\"#d1d5db\" stroke-width=\"1\" filter=\"url(#nodeShadow)\"/>\n",
            bx, by, boxW, boxH));
        sb.append(String.format(Locale.ROOT,
            "<text x=\"%.1f\" y=\"%.1f\" font-family=\"sans-serif\" font-size=\"11\" " +
            "font-weight=\"bold\" fill=\"#374151\">Legend</text>\n",
            bx + LEGEND_PAD, by + LEGEND_PAD + 4));
        int i = 0;
        for (String type : types) {
            double ry    = by + LEGEND_PAD + 20 + i * LEGEND_ROW_H;
            String color = EDGE_COLOR.getOrDefault(type, "#6b7280");
            String dash  = EDGE_DASH.get(type);
            String dashA = dash != null ? String.format(" stroke-dasharray=\"%s\"", dash) : "";
            sb.append(String.format(Locale.ROOT,
                "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" " +
                "stroke=\"%s\" stroke-width=\"2\"%s marker-end=\"url(#%s)\"/>\n",
                bx + LEGEND_PAD, ry + 8, bx + LEGEND_PAD + 36, ry + 8,
                color, dashA, markerIdFor(type, color)));
            sb.append(String.format(Locale.ROOT,
                "<text x=\"%.1f\" y=\"%.1f\" font-family=\"sans-serif\" font-size=\"11\" " +
                "fill=\"#374151\">%s</text>\n",
                bx + LEGEND_PAD + 44, ry + 12,
                escapeXml(type.toLowerCase(Locale.ROOT).replace('_', ' '))));
            i++;
        }
        return sb.toString();
    }

    // ------------------------------------------------------------------ CSS & SVG filters

    String defaultCss() {
        return "svg { font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', sans-serif; }\n"
             + ".node-rect { transition: filter 0.15s; }\n"
             + ".node-rect:hover { stroke-width: 2.5 !important; }\n"
             + ".edge-line { stroke-linecap: round; stroke-linejoin: round; }\n"
             + ".edge-label { paint-order: stroke; stroke: #f9fafb; stroke-width: 3; }\n"
             + ".group-rect { }\n"
             + ".node-label { pointer-events: none; }\n";
    }

    static String dropShadowFilter() {
        return "<filter id=\"nodeShadow\" x=\"-15%\" y=\"-15%\" width=\"130%\" height=\"130%\">\n"
             + "  <feDropShadow dx=\"0\" dy=\"2\" stdDeviation=\"3\" flood-color=\"#0000001a\"/>\n"
             + "</filter>\n";
    }

    // ------------------------------------------------------------------ sequence detection

    boolean isInteractionGraph(Graph graph) {
        if (graph.getNodes().size() < 2 || graph.getEdges().isEmpty()) return false;
        long methodCount = graph.getNodes().stream()
                .filter(n -> "METHOD".equals(n.getType())).count();
        if (methodCount < 2) return false;
        long interactionEdges = graph.getEdges().stream()
                .filter(e -> "CALLS".equals(e.getType()) || "EXCEPTION_PROPAGATION".equals(e.getType()))
                .count();
        if (interactionEdges * 2 < graph.getEdges().size()) return false;
        // Need at least one integer-labeled CALLS edge
        boolean hasIntegerCalls = graph.getEdges().stream()
                .filter(e -> "CALLS".equals(e.getType()))
                .anyMatch(e -> {
                    if (e.getLabel() == null) return false;
                    try { Integer.parseInt(e.getLabel()); return true; }
                    catch (NumberFormatException ex) { return false; }
                });
        if (!hasIntegerCalls) return false;
        return graph.getEdges().stream()
                .filter(e -> "CALLS".equals(e.getType()))
                .allMatch(e -> {
                    if (e.getLabel() == null) return false;
                    try { Integer.parseInt(e.getLabel()); return true; }
                    catch (NumberFormatException ex) { return false; }
                });
    }

    // ------------------------------------------------------------------ helpers

    double nodeRenderH(Node node) {
        Map<String, String> props = node.getProperties();
        String type = node.getType() != null ? node.getType() : "CLASS";
        boolean compartments = props != null
                && (props.containsKey("fields") || props.containsKey("methods"))
                && ("CLASS".equals(type) || "INTERFACE".equals(type));
        if (compartments) return computeCompartmentHeight(props);
        return node.getHeight() != null ? Math.max(node.getHeight(), RENDER_NODE_H) : RENDER_NODE_H;
    }

    private double[] computeBounds(Graph graph) {
        double maxX = MIN_WIDTH - PADDING, maxY = MIN_HEIGHT - PADDING;
        for (Node node : graph.getNodes()) {
            if (node.getX() == null) continue;
            maxX = Math.max(maxX, node.getX() + (node.getWidth()  != null ? node.getWidth()  : RENDER_NODE_W));
            maxY = Math.max(maxY, node.getY() + nodeRenderH(node));
        }
        return new double[]{maxX, maxY};
    }

    static String truncate(String s, int maxLen) {
        return s.length() <= maxLen ? s : s.substring(0, maxLen - 1) + "…";
    }

    String escapeXml(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&apos;");
    }
}
