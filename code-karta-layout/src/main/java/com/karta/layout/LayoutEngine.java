package com.karta.layout;

import com.karta.core.model.Graph;
import se.deversity.vibetags.annotations.AIContract;

@AIContract(reason = "layout(Graph) must return the same Graph instance (mutated in-place) so callers can chain. Implementations must set x, y, width, height on every Node. Nodes with unresolvable positions must be left at null — SvgRenderer silently skips them.")
public interface LayoutEngine {
    /**
     * Assigns x, y, width, height to every Node in the graph and returns
     * the same (mutated) Graph instance for fluent chaining.
     */
    Graph layout(Graph graph);
}
