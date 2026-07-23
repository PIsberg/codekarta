package se.deversity.codekarta.render;

import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Group;
import se.deversity.codekarta.core.model.Node;
import org.jspecify.annotations.Nullable;
import se.deversity.vibetags.annotations.AIArchitecture;
import se.deversity.vibetags.annotations.AIContext;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Renders interaction (sequence / exception-flow) diagrams in the classic UML style:
 * participant header boxes across the top, dashed lifelines down, horizontal message
 * arrows ordered by call-sequence label, activation bars, and try/catch region frames.
 *
 * Invoked automatically by {@link SvgRenderer} when {@link SvgRenderer#isInteractionGraph}
 * returns {@code true}.
 */
@AIContext(
    focus = "Participants are derived from METHOD node-id prefixes (before last dot). Messages are DFS-ordered by integer CALLS label from entry methods (no incoming CALLS). EXCEPTION nodes (id prefix 'exception:') are pinned last. Groups become UML region frames spanning the Y-range of their member messages.",
    avoids = "Reading Node.x/y from the Graph — this renderer ignores BFS coordinates entirely and computes its own lane geometry from LANE_W and participant index."
)
@AIArchitecture(belongsTo = "render", cannotReference = {"input", "layout", "cli"})
class SequenceDiagramRenderer {

    private static final double LANE_W          = 220.0;
    private static final double MARGIN_X        = 80.0;
    private static final double MARGIN_TOP      = 40.0;
    private static final double HEADER_W        = 170.0;
    private static final double HEADER_H        = 58.0;
    private static final double LIFELINE_EXTRA  = 50.0;
    private static final double STEP_GAP        = 54.0;
    private static final double DIAGRAM_OFFSET  = 32.0;
    private static final double ACTIVATION_W    = 10.0;
    private static final double ACTIVATION_H    = 26.0;
    private static final double LABEL_OFFSET    = 9.0;

    private final SvgRenderer parent;

    SequenceDiagramRenderer(SvgRenderer parent) {
        this.parent = parent;
    }

    // ------------------------------------------------------------------ public entry

    String render(Graph graph, @Nullable String cssOverride) {
        List<Participant> participants = buildParticipants(graph);
        List<Message>     messages    = orderMessages(graph);

        double svgWidth  = Math.max(960.0, MARGIN_X * 2 + participants.size() * LANE_W);
        double diagramY  = MARGIN_TOP + HEADER_H + DIAGRAM_OFFSET;
        double svgHeight = Math.max(560.0,
                diagramY + Math.max(1, messages.size()) * STEP_GAP
                        + LIFELINE_EXTRA + 60 + SvgRenderer.ATTRIBUTION_H);

        StringBuilder sb = new StringBuilder();
        sb.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n");
        sb.append(String.format(Locale.ROOT,
            "<svg xmlns=\"http://www.w3.org/2000/svg\" width=\"%.0f\" height=\"%.0f\" " +
            "viewBox=\"0 0 %.0f %.0f\">\n",
            svgWidth, svgHeight, svgWidth, svgHeight));

        sb.append("<defs>\n");
        sb.append("<style>\n").append(cssOverride != null ? cssOverride : parent.defaultCss()).append("</style>\n");
        sb.append(SvgRenderer.dropShadowFilter());
        sb.append(SvgRenderer.buildGradients());
        sb.append(SvgRenderer.dotGridPattern());
        sb.append(seqMarkers());
        sb.append("</defs>\n");

        sb.append(String.format(Locale.ROOT,
            "<rect width=\"%.0f\" height=\"%.0f\" fill=\"#f9fafb\"/>\n", svgWidth, svgHeight));
        sb.append(String.format(Locale.ROOT,
            "<rect width=\"%.0f\" height=\"%.0f\" fill=\"url(#dotGrid)\"/>\n", svgWidth, svgHeight));

        double lifelineBottom = diagramY + Math.max(1, messages.size()) * STEP_GAP + LIFELINE_EXTRA;

        // 1. Group frames (bottom layer)
        for (Group group : graph.getGroups()) {
            sb.append(renderFrame(group, messages, participants, diagramY));
        }

        // 2. Lifelines
        for (Participant p : participants) {
            double lx = laneX(p.lane);
            sb.append(String.format(Locale.ROOT,
                "<line x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" " +
                "stroke=\"#9ca3af\" stroke-width=\"1.2\" stroke-dasharray=\"6,4\"/>\n",
                lx, MARGIN_TOP + HEADER_H, lx, lifelineBottom));
        }

        // 3. Activation bars
        for (Message msg : messages) {
            if ("CALLS".equals(msg.type)) {
                Participant to = participantFor(msg.toId, participants);
                if (to != null) {
                    double ax = laneX(to.lane) - ACTIVATION_W / 2;
                    double ay = diagramY + msg.step * STEP_GAP - ACTIVATION_H / 4.0;
                    sb.append(String.format(Locale.ROOT,
                        "<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" " +
                        "fill=\"#ffffff\" stroke=\"#92400e\" stroke-width=\"0.9\"/>\n",
                        ax, ay, ACTIVATION_W, ACTIVATION_H));
                }
            }
        }

        // 4. Messages (arrows)
        for (Message msg : messages) {
            sb.append(renderMessage(msg, participants, diagramY));
        }

        // 5. Participant headers (top layer — drawn last so they occlude lifelines)
        for (Participant p : participants) {
            sb.append(renderHeader(p));
        }

        sb.append(parent.renderAttribution(svgWidth, svgHeight));
        sb.append(SvgRenderer.embeddedJs());
        sb.append("</svg>");
        return sb.toString();
    }

    // ------------------------------------------------------------------ participants

    List<Participant> buildParticipants(Graph graph) {
        // Maintain insertion order; CLASS/METHOD participants first, EXCEPTION last
        Map<String, String> seen = new LinkedHashMap<>();
        for (Node node : graph.getNodes()) {
            String type = node.getType() != null ? node.getType() : "CLASS";
            if ("METHOD".equals(type)) {
                String prefix = participantPrefix(node.getId());
                seen.putIfAbsent(prefix, "CLASS");
            } else if ("CLASS".equals(type)) {
                seen.putIfAbsent(node.getId(), "CLASS");
            }
        }

        List<Participant> result = new ArrayList<>();
        int lane = 0;
        for (Map.Entry<String, String> e : seen.entrySet()) {
            result.add(new Participant(e.getKey(), e.getKey(), e.getValue(), lane++));
        }
        for (Node node : graph.getNodes()) {
            if ("EXCEPTION".equals(node.getType())) {
                String label = node.getId().startsWith("exception:")
                        ? node.getId().substring("exception:".length()) : node.getId();
                result.add(new Participant(node.getId(), label, "EXCEPTION", lane++));
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ message ordering

    List<Message> orderMessages(Graph graph) {
        // adjacency: sourceId → outgoing CALLS edges, sorted by sequence integer label
        Map<String, List<Edge>> outgoing = new LinkedHashMap<>();
        Set<String> hasIncomingCalls = new HashSet<>();
        for (Edge e : graph.getEdges()) {
            if ("CALLS".equals(e.getType())) {
                outgoing.computeIfAbsent(e.getSourceId(), k -> new ArrayList<>()).add(e);
                hasIncomingCalls.add(e.getTargetId());
            }
        }
        outgoing.values().forEach(list ->
                list.sort(Comparator.comparingInt(e -> safeInt(e.getLabel()))));

        List<Message> messages = new ArrayList<>();
        int[] step = {0};
        Set<String> visitedEdges = new HashSet<>();

        // DFS from entry methods (no incoming CALLS)
        for (Node n : graph.getNodes()) {
            if ("METHOD".equals(n.getType()) && !hasIncomingCalls.contains(n.getId())) {
                dfs(n.getId(), outgoing, messages, step, visitedEdges);
            }
        }

        // EXCEPTION_PROPAGATION appended after CALLS in graph order
        for (Edge e : graph.getEdges()) {
            if ("EXCEPTION_PROPAGATION".equals(e.getType())) {
                String label = "throws";
                if (e.getTargetId() != null && e.getTargetId().startsWith("exception:")) {
                    label = "throws " + e.getTargetId().substring("exception:".length());
                }
                messages.add(new Message(e.getSourceId(), e.getTargetId(),
                        step[0]++, label, "EXCEPTION_PROPAGATION"));
            }
        }
        return messages;
    }

    private void dfs(String nodeId, Map<String, List<Edge>> outgoing,
                     List<Message> messages, int[] step, Set<String> visitedEdges) {
        for (Edge e : outgoing.getOrDefault(nodeId, List.of())) {
            if (!visitedEdges.add(e.getId())) continue; // guard against cycles
            String methodName = simpleMethodName(e.getTargetId());
            String seqNum     = e.getLabel() != null ? e.getLabel() : "";
            messages.add(new Message(e.getSourceId(), e.getTargetId(), step[0]++,
                    seqNum.isEmpty() ? methodName : seqNum + ": " + methodName, "CALLS"));
            dfs(e.getTargetId(), outgoing, messages, step, visitedEdges);
        }
    }

    // ------------------------------------------------------------------ rendering

    private String renderHeader(Participant p) {
        double cx     = laneX(p.lane);
        double x      = cx - HEADER_W / 2;
        String fill = "url(#grad-" + p.type + ")";
        if (!SvgRenderer.NODE_FILL.containsKey(p.type)) {
            fill = "url(#grad-CLASS)";
        }
        String stroke = SvgRenderer.NODE_STROKE.getOrDefault(p.type, "#374151");
        String stereo = SvgRenderer.STEREOTYPE.get(p.type);
        String label  = parent.escapeXml(p.label);

        StringBuilder sb = new StringBuilder();
        sb.append(String.format(Locale.ROOT,
            "<rect class=\"node-rect\" x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" " +
            "rx=\"6\" fill=\"%s\" stroke=\"%s\" stroke-width=\"1.8\" filter=\"url(#nodeShadow)\"/>\n",
            x, MARGIN_TOP, HEADER_W, HEADER_H, fill, stroke));
        if (stereo != null) {
            sb.append(String.format(Locale.ROOT,
                "<text class=\"node-label\" x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" " +
                "font-family=\"sans-serif\" font-size=\"10\" fill=\"%s\" " +
                "font-style=\"italic\">%s</text>\n",
                cx, MARGIN_TOP + HEADER_H * 0.34, stroke, parent.escapeXml(stereo)));
            sb.append(String.format(Locale.ROOT,
                "<text class=\"node-label\" x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" " +
                "dominant-baseline=\"central\" font-family=\"sans-serif\" font-size=\"13\" " +
                "font-weight=\"bold\" fill=\"#1f2937\">%s</text>\n",
                cx, MARGIN_TOP + HEADER_H * 0.7, label));
        } else {
            sb.append(String.format(Locale.ROOT,
                "<text class=\"node-label\" x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" " +
                "dominant-baseline=\"central\" font-family=\"sans-serif\" font-size=\"13\" " +
                "font-weight=\"bold\" fill=\"#1f2937\">%s</text>\n",
                cx, MARGIN_TOP + HEADER_H / 2, label));
        }
        return sb.toString();
    }

    private String renderMessage(Message msg, List<Participant> participants, double diagramY) {
        Participant from = participantFor(msg.fromId, participants);
        Participant to   = participantFor(msg.toId, participants);
        if (from == null || to == null) return "";

        double arrowY  = diagramY + msg.step * STEP_GAP;
        double fromX   = laneX(from.lane);
        double toX     = laneX(to.lane);
        String color   = SvgRenderer.EDGE_COLOR.getOrDefault(msg.type, "#6b7280");
        boolean isExc  = "EXCEPTION_PROPAGATION".equals(msg.type);
        String markerId = isExc ? "seq-marker-exc" : "seq-marker-call";
        String dashAttr = isExc ? " stroke-dasharray=\"6,3\"" : "";

        StringBuilder sb = new StringBuilder();

        if (from.lane == to.lane) {
            // Self-call: small loop to the right
            double lx = fromX + ACTIVATION_W / 2;
            double loopW = 36, yEnd = arrowY + STEP_GAP * 0.45;
            sb.append(String.format(Locale.ROOT,
                "<path class=\"edge-line\" data-source=\"%s\" data-target=\"%s\" data-type=\"%s\" d=\"M%.1f %.1f C%.1f %.1f %.1f %.1f %.1f %.1f\" " +
                "fill=\"none\" stroke=\"%s\" stroke-width=\"1.6\"%s marker-end=\"url(#%s)\"/>\n",
                parent.escapeXml(msg.fromId), parent.escapeXml(msg.toId), parent.escapeXml(msg.type),
                lx, arrowY,
                lx + loopW, arrowY - 5,
                lx + loopW, yEnd + 5,
                lx, yEnd,
                color, dashAttr, markerId));
            sb.append(String.format(Locale.ROOT,
                "<text class=\"edge-label\" data-source=\"%s\" data-target=\"%s\" data-type=\"%s\" x=\"%.1f\" y=\"%.1f\" text-anchor=\"start\" " +
                "font-family=\"sans-serif\" font-size=\"10\" fill=\"%s\" " +
                "font-style=\"italic\">%s</text>\n",
                parent.escapeXml(msg.fromId), parent.escapeXml(msg.toId), parent.escapeXml(msg.type),
                lx + loopW + 4, arrowY + 4, color, parent.escapeXml(msg.label)));
        } else {
            boolean goRight = toX > fromX;
            double arrowToX = toX + (goRight ? -ACTIVATION_W / 2 : ACTIVATION_W / 2);
            sb.append(String.format(Locale.ROOT,
                "<line class=\"edge-line\" data-source=\"%s\" data-target=\"%s\" data-type=\"%s\" x1=\"%.1f\" y1=\"%.1f\" x2=\"%.1f\" y2=\"%.1f\" " +
                "stroke=\"%s\" stroke-width=\"1.6\"%s marker-end=\"url(#%s)\"/>\n",
                parent.escapeXml(msg.fromId), parent.escapeXml(msg.toId), parent.escapeXml(msg.type),
                fromX, arrowY, arrowToX, arrowY, color, dashAttr, markerId));
            double lx = (fromX + toX) / 2;
            sb.append(String.format(Locale.ROOT,
                "<text class=\"edge-label\" data-source=\"%s\" data-target=\"%s\" data-type=\"%s\" x=\"%.1f\" y=\"%.1f\" text-anchor=\"middle\" " +
                "font-family=\"sans-serif\" font-size=\"10\" fill=\"%s\" " +
                "font-style=\"italic\">%s</text>\n",
                parent.escapeXml(msg.fromId), parent.escapeXml(msg.toId), parent.escapeXml(msg.type),
                lx, arrowY - LABEL_OFFSET, color, parent.escapeXml(msg.label)));
        }
        return sb.toString();
    }

    private String renderFrame(Group group, List<Message> messages,
                                List<Participant> participants, double diagramY) {
        Set<String> memberIds = new HashSet<>(group.getMemberIds());
        List<Message> memberMsgs = messages.stream()
                .filter(m -> memberIds.contains(m.toId))
                .toList();
        if (memberMsgs.isEmpty()) return "";

        int minStep = memberMsgs.stream().mapToInt(m -> m.step).min().orElse(0);
        int maxStep = memberMsgs.stream().mapToInt(m -> m.step).max().orElse(0);

        int minLane = participants.size(), maxLane = 0;
        for (Message m : memberMsgs) {
            Participant from = participantFor(m.fromId, participants);
            Participant to   = participantFor(m.toId,   participants);
            if (from != null) { minLane = Math.min(minLane, from.lane); maxLane = Math.max(maxLane, from.lane); }
            if (to   != null) { minLane = Math.min(minLane, to.lane);   maxLane = Math.max(maxLane, to.lane); }
        }

        double pad  = 14;
        double fx   = laneX(minLane) - HEADER_W / 2 - pad;
        double fy   = diagramY + minStep * STEP_GAP - 18;
        double fw   = laneX(maxLane) - laneX(minLane) + HEADER_W + pad * 2;
        double fh   = Math.max(36, (maxStep - minStep + 1) * STEP_GAP + 20);
        String lbl  = parent.escapeXml(group.getLabel() != null ? group.getLabel() : group.getId());
        double tabW = Math.min(fw - 4, lbl.length() * 7.0 + 12);

        return String.format(Locale.ROOT,
            "<rect class=\"group-rect\" x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"%.1f\" " +
            "rx=\"4\" fill=\"#fef9c3\" fill-opacity=\"0.35\" stroke=\"#ca8a04\" " +
            "stroke-dasharray=\"6,3\" stroke-width=\"1.2\"/>\n" +
            "<rect x=\"%.1f\" y=\"%.1f\" width=\"%.1f\" height=\"16\" rx=\"2\" fill=\"#fef08a\"/>\n" +
            "<text x=\"%.1f\" y=\"%.1f\" font-family=\"sans-serif\" font-size=\"10\" " +
            "fill=\"#713f12\">%s</text>\n",
            fx, fy, fw, fh,
            fx, fy, tabW,
            fx + 4, fy + 11,
            lbl);
    }

    // ------------------------------------------------------------------ markers

    private static String seqMarkers() {
        String callColor = SvgRenderer.EDGE_COLOR.getOrDefault("CALLS", "#92400e");
        String excColor  = SvgRenderer.EDGE_COLOR.getOrDefault("EXCEPTION_PROPAGATION", "#dc2626");
        return SvgRenderer.buildMarkerSvg("seq-marker-call", "filled", callColor)
             + SvgRenderer.buildMarkerSvg("seq-marker-exc",  "filled", excColor);
    }

    // ------------------------------------------------------------------ geometry

    private double laneX(int lane) {
        return MARGIN_X + lane * LANE_W + LANE_W / 2;
    }

    // ------------------------------------------------------------------ data / utilities

    static Participant participantFor(String nodeId, List<Participant> participants) {
        if (nodeId == null || participants == null) return null;
        String prefix = participantPrefix(nodeId);
        for (Participant p : participants) {
            if (p.id.equals(nodeId) || p.id.equals(prefix)) return p;
        }
        return null;
    }

    static String participantPrefix(String nodeId) {
        int dot = nodeId.lastIndexOf('.');
        return dot > 0 ? nodeId.substring(0, dot) : nodeId;
    }

    static String simpleMethodName(String nodeId) {
        int dot = nodeId.lastIndexOf('.');
        return dot >= 0 ? nodeId.substring(dot + 1) : nodeId;
    }

    static int safeInt(String s) {
        if (s == null) return 0;
        try { return Integer.parseInt(s.trim()); } catch (NumberFormatException e) { return 0; }
    }

    record Participant(String id, String label, String type, int lane) {}

    record Message(String fromId, String toId, int step, String label, String type) {}
}
