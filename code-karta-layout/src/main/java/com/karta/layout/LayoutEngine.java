package com.karta.layout;

import com.karta.core.model.Graph;

public interface LayoutEngine {
    /**
     * Assigns x, y, width, height to every Node in the graph and returns
     * the same (mutated) Graph instance for fluent chaining.
     */
    Graph layout(Graph graph);
}
