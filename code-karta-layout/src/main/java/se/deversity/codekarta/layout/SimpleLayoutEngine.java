package se.deversity.codekarta.layout;

import se.deversity.codekarta.core.model.Edge;
import se.deversity.codekarta.core.model.Graph;
import se.deversity.codekarta.core.model.Node;
import se.deversity.codekarta.core.model.NodeDimensions;
import se.deversity.vibetags.annotations.AIArchitecture;
import se.deversity.vibetags.annotations.AIContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;

/**
 * Pure-Java BFS hierarchical layout.
 *
 * Algorithm:
 *  1. Identify root nodes (no incoming edges). If every node has incoming edges
 *     (cycle), treat the first node as the root.
 *  2. BFS assigns each node the maximum depth at which it is reachable from any root.
 *  3. Nodes at the same depth are placed side by side (column); depths are rows.
 *  4. Row Y positions are computed from the tallest estimated rendered height in each
 *     row so that class-diagram compartment nodes never bleed into the row below.
 */
@AIContext(
    focus = "BFS from root nodes (no incoming edges) assigns depth levels → rows; siblings within a row become columns. Isolated nodes fall back to level 0. Cyclic graphs seed BFS from the first node. Row Y positions are computed dynamically from the tallest estimated node height in each row so that compartment-heavy class nodes never overlap the row below.",
    avoids = "Changing NodeDimensions.DEFAULT_WIDTH/HEIGHT — those constants are @AILocked and consumed by both layout engines and SvgRenderer."
)
@AIArchitecture(belongsTo = "layout", cannotReference = {"input", "render", "cli"})
public class SimpleLayoutEngine implements LayoutEngine {

    private static final double NODE_WIDTH  = NodeDimensions.DEFAULT_WIDTH;
    private static final double NODE_HEIGHT = NodeDimensions.DEFAULT_HEIGHT;
    private static final double H_GAP       = 80.0;
    private static final double V_GAP       = 120.0;
    private static final double MARGIN      = 60.0;

    /**
     * Width at which one depth level wraps onto a further sub-row.
     *
     * <p>Without a bound, a level is an unbounded row. A package of ~120 classes, or an enum of
     * 127 constants with no transitions between them, all sit at depth 0 and produce a canvas
     * around 19500px wide and 1600px tall — nothing can read that, and the ELK engine renders the
     * same graph in about 2300px. Wrapping keeps a level's nodes contiguous, so "these are all at
     * depth N" still reads, while bounding the canvas to something a browser and a human can
     * handle.
     */
    static final double MAX_ROW_WIDTH = 2600.0;

    // Compartment height constants mirrored from SvgRenderer (kept in sync manually).
    // Used only for row-spacing estimation — never for actual pixel output.
    private static final double COMPARTMENT_HEADER_H  = 44.0;
    private static final double COMPARTMENT_SECTION_H = 13.0; // padding*2 + 1
    private static final double COMPARTMENT_LINE_H    = 15.0;

    @Override
    public Graph layout(Graph graph) {
        List<Node> nodes = graph.getNodes();
        if (nodes.isEmpty()) return graph;

        Map<String, Integer> levels = computeLevels(graph);
        Map<Integer, List<String>> byLevel = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : levels.entrySet()) {
            byLevel.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        int perRow = columnsPerRow();

        // Pass 1: compute cumulative row Y positions based on the tallest estimated rendered
        // height in each level, so compartment nodes never overlap the next level. A level wider
        // than MAX_ROW_WIDTH occupies several sub-rows, each of which needs that height.
        double curY = MARGIN;
        Map<Integer, Double> rowY = new TreeMap<>();
        for (Map.Entry<Integer, List<String>> entry : byLevel.entrySet()) {
            int row = entry.getKey();
            rowY.put(row, curY);
            double maxH = levelHeight(graph, entry.getValue());
            int subRows = subRowCount(entry.getValue().size(), perRow);
            curY += subRows * (maxH + V_GAP);
        }

        // Pass 2: assign coordinates, wrapping each level across sub-rows.
        for (Map.Entry<Integer, List<String>> entry : byLevel.entrySet()) {
            int row = entry.getKey();
            List<String> ids = entry.getValue();
            double levelY = rowY.get(row);
            double maxH = levelHeight(graph, ids);
            for (int i = 0; i < ids.size(); i++) {
                Node node = graph.findNode(ids.get(i));
                if (node != null) {
                    // Both are deliberate integer positions within the level: the column across a
                    // sub-row, and the index of the sub-row itself. Named so the integer division
                    // reads as intent rather than as an accidental truncation.
                    int col = i % perRow;
                    int subRow = i / perRow;
                    node.setX(MARGIN + col * (NODE_WIDTH + H_GAP));
                    node.setY(levelY + subRow * (maxH + V_GAP));
                    node.setWidth(NODE_WIDTH);
                    node.setHeight(NODE_HEIGHT);
                }
            }
        }
        return graph;
    }

    /** Nodes per sub-row. At least one, so a node wider than the bound still lays out. */
    static int columnsPerRow() {
        int fit = (int) (MAX_ROW_WIDTH / (NODE_WIDTH + H_GAP));
        return fit < 1 ? 1 : fit;
    }

    /** Sub-rows needed to hold {@code count} nodes at {@code perRow} each. */
    static int subRowCount(int count, int perRow) {
        return count <= 0 ? 0 : (count + perRow - 1) / perRow;
    }

    /** Tallest estimated rendered height among {@code ids}; the row pitch for that level. */
    private double levelHeight(Graph graph, List<String> ids) {
        double maxH = NODE_HEIGHT;
        for (String id : ids) {
            Node node = graph.findNode(id);
            if (node != null) maxH = Math.max(maxH, estimateRenderHeight(node));
        }
        return maxH;
    }

    /**
     * Estimates the rendered height of a node by mirroring SvgRenderer's compartment
     * height formula. Nodes with {@code fields} or {@code methods} properties grow
     * taller than {@link NodeDimensions#DEFAULT_HEIGHT}; all other nodes return
     * {@code NODE_HEIGHT}.
     */
    double estimateRenderHeight(Node node) {
        Map<String, String> props = node.getProperties();
        if (props == null) return NODE_HEIGHT;
        String fields  = props.get("fields");
        String methods = props.get("methods");
        if ((fields == null || fields.isEmpty()) && (methods == null || methods.isEmpty())) {
            return NODE_HEIGHT;
        }
        double h = COMPARTMENT_HEADER_H;
        if (fields  != null && !fields.isEmpty())
            h += COMPARTMENT_SECTION_H + fields.split("\n").length  * COMPARTMENT_LINE_H;
        if (methods != null && !methods.isEmpty())
            h += COMPARTMENT_SECTION_H + methods.split("\n").length * COMPARTMENT_LINE_H;
        return Math.max(h, NODE_HEIGHT);
    }

    private Map<String, Integer> computeLevels(Graph graph) {
        Set<String> hasIncoming = new HashSet<>();
        for (Edge edge : graph.getEdges()) {
            hasIncoming.add(edge.getTargetId());
        }

        Map<String, Integer> levels = new HashMap<>();
        Queue<String> queue = new ArrayDeque<>();

        for (Node node : graph.getNodes()) {
            if (!hasIncoming.contains(node.getId())) {
                levels.put(node.getId(), 0);
                queue.add(node.getId());
            }
        }

        // Cycle fallback: seed with first node
        if (queue.isEmpty() && !graph.getNodes().isEmpty()) {
            String firstId = graph.getNodes().get(0).getId();
            levels.put(firstId, 0);
            queue.add(firstId);
        }

        Set<String> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (!visited.add(id)) continue;
            int level = levels.getOrDefault(id, 0);
            for (Edge edge : graph.getEdges()) {
                if (id.equals(edge.getSourceId())) {
                    String target = edge.getTargetId();
                    int proposed = level + 1;
                    if (proposed > levels.getOrDefault(target, -1)) {
                        levels.put(target, proposed);
                        queue.add(target);
                    }
                }
            }
        }

        // Assign level 0 to any nodes not reached by BFS (isolated)
        for (Node node : graph.getNodes()) {
            levels.putIfAbsent(node.getId(), 0);
        }

        return levels;
    }
}
