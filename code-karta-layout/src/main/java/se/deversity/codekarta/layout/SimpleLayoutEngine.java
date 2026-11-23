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
 *  5. A graph with groups is laid out one block per group (then the ungrouped nodes), each
 *     by steps 1-4 over its own members, and the blocks are packed left to right, so the frame
 *     drawn around a group holds that group's nodes and no others.
 */
@AIContext(
    focus = "BFS from root nodes (no incoming edges) assigns depth levels → rows; siblings within a row become columns. Isolated nodes fall back to level 0. Cyclic graphs seed BFS from the first node. Row Y positions are computed dynamically from the tallest estimated node height in each row so that compartment-heavy class nodes never overlap the row below. With groups, each group is its own block laid out over its members and the blocks are packed side by side, so group frames never overlap.",
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


    /**
     * Space between two group blocks, horizontally and vertically. Wider than twice the 14px
     * padding SvgRenderer draws around a group, so neighbouring frames stay apart.
     */
    static final double GROUP_GAP = 80.0;

    @Override
    public Graph layout(Graph graph) {
        List<Node> nodes = graph.getNodes();
        if (nodes.isEmpty()) return graph;

        List<Set<String>> blocks = blocks(graph);
        if (blocks.size() <= 1) {
            layoutBlock(graph, blocks.isEmpty() ? Set.of() : blocks.get(0), MARGIN, MARGIN);
            return graph;
        }

        // One block per group, then the ungrouped nodes, packed left to right and wrapped at
        // MAX_ROW_WIDTH. Laying the whole graph out by depth instead scattered each package's
        // classes among the others', and the frame drawn around a package swallowed classes
        // that do not belong to it (issue #272).
        double x = MARGIN;
        double y = MARGIN;
        double rowHeight = 0;
        for (Set<String> block : blocks) {
            double[] size = layoutBlock(graph, block, 0, 0);
            if (x > MARGIN && x + size[0] > MARGIN + MAX_ROW_WIDTH) {
                x = MARGIN;
                y += rowHeight + GROUP_GAP;
                rowHeight = 0;
            }
            for (String id : block) {
                Node node = graph.findNode(id);
                Double nodeX = node == null ? null : node.getX();
                Double nodeY = node == null ? null : node.getY();
                if (nodeX != null && nodeY != null) {
                    node.setX(nodeX + x);
                    node.setY(nodeY + y);
                }
            }
            x += size[0] + GROUP_GAP;
            rowHeight = Math.max(rowHeight, size[1]);
        }
        return graph;
    }

    /**
     * The node sets laid out separately: each group's members (a node listed by two groups goes
     * with the first, as {@code Group} documents one enclosing group per node), then the nodes no
     * group lists. A graph without groups is one block, laid out exactly as before groups were read.
     */
    private static List<Set<String>> blocks(Graph graph) {
        Set<String> existing = new java.util.LinkedHashSet<>();
        graph.getNodes().forEach(n -> existing.add(n.getId()));
        List<Set<String>> blocks = new ArrayList<>();
        Set<String> assigned = new HashSet<>();
        for (se.deversity.codekarta.core.model.Group group : graph.getGroups()) {
            Set<String> members = new java.util.LinkedHashSet<>();
            for (String id : group.getMemberIds()) {
                if (existing.contains(id) && assigned.add(id)) {
                    members.add(id);
                }
            }
            if (!members.isEmpty()) {
                blocks.add(members);
            }
        }
        Set<String> rest = new java.util.LinkedHashSet<>(existing);
        rest.removeAll(assigned);
        if (!rest.isEmpty()) {
            blocks.add(rest);
        }
        return blocks;
    }

    /**
     * Lays out {@code members} by depth from {@code (originX, originY)}, counting only the edges
     * between members, and returns the block's {width, height}.
     */
    private double[] layoutBlock(Graph graph, Set<String> members, double originX, double originY) {
        Map<String, Integer> levels = computeLevels(graph, members);
        Map<Integer, List<String>> byLevel = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : levels.entrySet()) {
            byLevel.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        int perRow = columnsPerRow();

        // Pass 1: compute cumulative row Y positions based on the tallest estimated rendered
        // height in each level, so compartment nodes never overlap the next level. A level wider
        // than MAX_ROW_WIDTH occupies several sub-rows, each of which needs that height.
        double curY = originY;
        Map<Integer, Double> rowY = new TreeMap<>();
        for (Map.Entry<Integer, List<String>> entry : byLevel.entrySet()) {
            int row = entry.getKey();
            rowY.put(row, curY);
            double maxH = levelHeight(graph, entry.getValue());
            int subRows = subRowCount(entry.getValue().size(), perRow);
            curY += subRows * (maxH + V_GAP);
        }

        // Pass 2: assign coordinates, wrapping each level across sub-rows.
        double width = 0;
        double height = 0;
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
                    double nodeX = originX + col * (NODE_WIDTH + H_GAP);
                    double nodeY = levelY + subRow * (maxH + V_GAP);
                    node.setX(nodeX);
                    node.setY(nodeY);
                    node.setWidth(NODE_WIDTH);
                    node.setHeight(NODE_HEIGHT);
                    width = Math.max(width, nodeX - originX + NODE_WIDTH);
                    height = Math.max(height, nodeY - originY + estimateRenderHeight(node));
                }
            }
        }
        return new double[] { width, height };
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

    /** See {@link RenderedSize#estimateHeight(Node)}, which both layout engines share. */
    double estimateRenderHeight(Node node) {
        return RenderedSize.estimateHeight(node);
    }

    private Map<String, Integer> computeLevels(Graph graph, Set<String> members) {
        List<Edge> edges = new ArrayList<>();
        for (Edge edge : graph.getEdges()) {
            if (members.contains(edge.getSourceId()) && members.contains(edge.getTargetId())) {
                edges.add(edge);
            }
        }
        List<Node> nodes = new ArrayList<>();
        for (Node node : graph.getNodes()) {
            if (members.contains(node.getId())) {
                nodes.add(node);
            }
        }
        Set<String> hasIncoming = new HashSet<>();
        for (Edge edge : edges) {
            hasIncoming.add(edge.getTargetId());
        }

        Map<String, Integer> levels = new HashMap<>();
        Queue<String> queue = new ArrayDeque<>();

        for (Node node : nodes) {
            if (!hasIncoming.contains(node.getId())) {
                levels.put(node.getId(), 0);
                queue.add(node.getId());
            }
        }

        // Cycle fallback: seed with first node
        if (queue.isEmpty() && !nodes.isEmpty()) {
            String firstId = nodes.get(0).getId();
            levels.put(firstId, 0);
            queue.add(firstId);
        }

        Set<String> visited = new HashSet<>();
        while (!queue.isEmpty()) {
            String id = queue.poll();
            if (!visited.add(id)) continue;
            int level = levels.getOrDefault(id, 0);
            for (Edge edge : edges) {
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
        for (Node node : nodes) {
            levels.putIfAbsent(node.getId(), 0);
        }

        return levels;
    }
}
