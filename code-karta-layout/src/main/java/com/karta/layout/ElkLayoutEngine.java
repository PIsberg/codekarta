package com.karta.layout;

import com.karta.core.model.Edge;
import com.karta.core.model.Graph;
import com.karta.core.model.Node;
import org.eclipse.elk.core.IGraphLayoutEngine;
import org.eclipse.elk.core.RecursiveGraphLayoutEngine;
import org.eclipse.elk.core.options.CoreOptions;
import org.eclipse.elk.core.options.Direction;
import org.eclipse.elk.core.util.BasicProgressMonitor;
import org.eclipse.elk.graph.ElkNode;
import org.eclipse.elk.graph.util.ElkGraphUtil;

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
public class ElkLayoutEngine implements LayoutEngine {

    private static final Logger log = Logger.getLogger(ElkLayoutEngine.class.getName());

    private static final double NODE_WIDTH  = 180.0;
    private static final double NODE_HEIGHT = 70.0;

    @Override
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

        Map<String, ElkNode> elkNodes = new HashMap<>();
        for (Node node : graph.getNodes()) {
            ElkNode elkNode = ElkGraphUtil.createNode(root);
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

        for (ElkNode elkNode : root.getChildren()) {
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
