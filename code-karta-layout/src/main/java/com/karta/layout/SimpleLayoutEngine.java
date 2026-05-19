package com.karta.layout;

import com.karta.core.model.Edge;
import com.karta.core.model.Graph;
import com.karta.core.model.Node;
import com.karta.core.model.NodeDimensions;
import se.deversity.vibetags.annotations.AIContext;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.TreeMap;

@AIContext(
    focus = "BFS from root nodes (no incoming edges) assigns depth levels → rows; siblings within a row become columns. Isolated nodes fall back to level 0. Cyclic graphs seed BFS from the first node.",
    avoids = "Changing NodeDimensions.DEFAULT_WIDTH/HEIGHT — those constants are @AILocked and consumed by both layout engines and SvgRenderer."
)
/**
 * Pure-Java BFS hierarchical layout.
 *
 * Algorithm:
 *  1. Identify root nodes (no incoming edges). If every node has incoming edges
 *     (cycle), treat the first node as the root.
 *  2. BFS assigns each node the maximum depth at which it is reachable from any root.
 *  3. Nodes at the same depth are placed side by side (column); depths are rows.
 */
public class SimpleLayoutEngine implements LayoutEngine {

    private static final double NODE_WIDTH  = NodeDimensions.DEFAULT_WIDTH;
    private static final double NODE_HEIGHT = NodeDimensions.DEFAULT_HEIGHT;
    private static final double H_GAP       = 80.0;
    private static final double V_GAP       = 120.0;
    private static final double MARGIN      = 60.0;

    @Override
    public Graph layout(Graph graph) {
        List<Node> nodes = graph.getNodes();
        if (nodes.isEmpty()) return graph;

        Map<String, Integer> levels = computeLevels(graph);
        Map<Integer, List<String>> byLevel = new TreeMap<>();
        for (Map.Entry<String, Integer> entry : levels.entrySet()) {
            byLevel.computeIfAbsent(entry.getValue(), k -> new ArrayList<>()).add(entry.getKey());
        }

        for (Map.Entry<Integer, List<String>> entry : byLevel.entrySet()) {
            int row = entry.getKey();
            List<String> ids = entry.getValue();
            for (int col = 0; col < ids.size(); col++) {
                Node node = graph.findNode(ids.get(col));
                if (node != null) {
                    node.setX(MARGIN + col * (NODE_WIDTH + H_GAP));
                    node.setY(MARGIN + row * (NODE_HEIGHT + V_GAP));
                    node.setWidth(NODE_WIDTH);
                    node.setHeight(NODE_HEIGHT);
                }
            }
        }
        return graph;
    }

    private Map<String, Integer> computeLevels(Graph graph) {
        Set<String> hasIncoming = new HashSet<>();
        for (Edge edge : graph.getEdges()) {
            hasIncoming.add(edge.getTargetId());
        }

        Map<String, Integer> levels = new HashMap<>();
        Queue<String> queue = new LinkedList<>();

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
