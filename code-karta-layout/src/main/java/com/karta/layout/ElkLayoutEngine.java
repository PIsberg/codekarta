package com.karta.layout;

import com.karta.core.model.Edge;
import com.karta.core.model.Graph;
import com.karta.core.model.Group;
import com.karta.core.model.Node;
import com.karta.core.model.NodeDimensions;
import org.eclipse.elk.core.IGraphLayoutEngine;
import org.eclipse.elk.core.RecursiveGraphLayoutEngine;
import org.eclipse.elk.core.options.CoreOptions;
import org.eclipse.elk.core.options.Direction;
import org.eclipse.elk.core.util.BasicProgressMonitor;
import org.eclipse.elk.graph.ElkNode;
import org.eclipse.elk.graph.util.ElkGraphUtil;

import se.deversity.vibetags.annotations.AIArchitecture;
import se.deversity.vibetags.annotations.AIContext;
import se.deversity.vibetags.annotations.AIPerformance;
import se.deversity.vibetags.annotations.AIStrictClasspath;

import java.util.HashMap;
import java.util.Map;
import java.util.logging.Logger;

/**
 * Layout engine backed by the Eclipse Layout Kernel (ELK) layered algorithm.
 *
 * <p>ELK's layered algorithm implements a full Sugiyama-framework pipeline —
 * layer assignment, crossing minimisation, node placement, and edge routing —
 * producing compact, orthogonally-routed diagrams that scale to large class
 * structures without the horizontal explosion of a naive BFS grid layout.
 *
 * <p>If ELK layout fails for any reason (missing service-loader registration,
 * unsupported graph property, etc.) the engine falls back transparently to
 * {@link SimpleLayoutEngine} so the pipeline never produces an empty diagram.
 */
@AIContext(focus = "Group members must be laid out as children of compound ElkNodes; absolute coordinates are compound.x + child.x. ELK's SPI entries must be merged in the fat JAR.", avoids = "Adding ELK options that are unsupported by the layered algorithm — any unknown property silently breaks layout and triggers the SimpleLayoutEngine fallback.")
@AIArchitecture(belongsTo = "layout", cannotReference = {"input", "render", "cli"})
@AIStrictClasspath
public class ElkLayoutEngine implements LayoutEngine {

    private static final Logger log = Logger.getLogger(ElkLayoutEngine.class.getName());

    private static final double NODE_WIDTH  = NodeDimensions.DEFAULT_WIDTH;
    private static final double NODE_HEIGHT = NodeDimensions.DEFAULT_HEIGHT;

    @Override
    @AIPerformance(constraint = "Layout runs synchronously in the CLI pipeline — avoid O(n²) or heap-allocating operations on the full node list. ELK's layered algorithm is already O(n log n); the fallback SimpleLayoutEngine is O(n).")
    public Graph layout(Graph graph) {
        if (graph.getNodes().isEmpty()) return graph;
        try {
            return layoutWithElk(graph);
        } catch (Exception e) {
            log.warning("ELK layout failed (" + e.getMessage() + "), falling back to SimpleLayoutEngine");
            return new SimpleLayoutEngine().layout(graph);
        }
    }

    private Graph layoutWithElk(Graph graph) {
        ElkNode root = ElkGraphUtil.createGraph();
        root.setProperty(CoreOptions.ALGORITHM, "org.eclipse.elk.layered");
        root.setProperty(CoreOptions.DIRECTION, Direction.DOWN);

        // Build reverse lookup: nodeId → groupId
        Map<String, String> nodeToGroup = new HashMap<>();
        for (Group group : graph.getGroups()) {
            for (String memberId : group.getMemberIds()) {
                nodeToGroup.put(memberId, group.getId());
            }
        }

        // Create compound ElkNode for each group that has members
        Map<String, ElkNode> groupElkNodes = new HashMap<>();
        for (Group group : graph.getGroups()) {
            if (!group.getMemberIds().isEmpty()) {
                ElkNode compound = ElkGraphUtil.createNode(root);
                compound.setIdentifier(group.getId());
                compound.setProperty(CoreOptions.ALGORITHM, "org.eclipse.elk.layered");
                groupElkNodes.put(group.getId(), compound);
            }
        }

        // Create leaf ElkNodes under the correct parent (compound or root)
        Map<String, ElkNode> elkNodes = new HashMap<>();
        for (Node node : graph.getNodes()) {
            String groupId = nodeToGroup.get(node.getId());
            ElkNode parent = groupId != null && groupElkNodes.containsKey(groupId)
                    ? groupElkNodes.get(groupId) : root;
            ElkNode elkNode = ElkGraphUtil.createNode(parent);
            elkNode.setIdentifier(node.getId());
            elkNode.setWidth(NODE_WIDTH);
            elkNode.setHeight(NODE_HEIGHT);
            elkNodes.put(node.getId(), elkNode);
        }

        for (Edge edge : graph.getEdges()) {
            ElkNode src = elkNodes.get(edge.getSourceId());
            ElkNode tgt = elkNodes.get(edge.getTargetId());
            if (src != null && tgt != null && src != tgt) {
                ElkGraphUtil.createSimpleEdge(src, tgt);
            }
        }

        IGraphLayoutEngine engine = new RecursiveGraphLayoutEngine();
        engine.layout(root, new BasicProgressMonitor());

        // Read back positions for grouped nodes (compound.x + member.x = absolute)
        for (Map.Entry<String, ElkNode> entry : groupElkNodes.entrySet()) {
            ElkNode compound = entry.getValue();
            double cx = compound.getX();
            double cy = compound.getY();
            for (ElkNode child : compound.getChildren()) {
                Node node = graph.findNode(child.getIdentifier());
                if (node != null) {
                    node.setX(cx + child.getX());
                    node.setY(cy + child.getY());
                    node.setWidth(child.getWidth());
                    node.setHeight(child.getHeight());
                }
            }
        }

        // Read back positions for non-grouped (root-level) nodes
        for (ElkNode elkNode : root.getChildren()) {
            if (groupElkNodes.containsKey(elkNode.getIdentifier())) continue;
            Node node = graph.findNode(elkNode.getIdentifier());
            if (node != null) {
                node.setX(elkNode.getX());
                node.setY(elkNode.getY());
                node.setWidth(elkNode.getWidth());
                node.setHeight(elkNode.getHeight());
            }
        }

        return graph;
    }
}
