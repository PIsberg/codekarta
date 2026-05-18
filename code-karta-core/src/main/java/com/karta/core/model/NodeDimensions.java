package com.karta.core.model;

import se.deversity.vibetags.annotations.AILocked;

@AILocked(reason = "Shared sizing constants consumed by all three tiers — SimpleLayoutEngine, ElkLayoutEngine, and SvgRenderer. Changing these values shifts node dimensions globally and will break layout tests.")
public final class NodeDimensions {

    public static final double DEFAULT_WIDTH  = 180.0;
    public static final double DEFAULT_HEIGHT = 70.0;

    private NodeDimensions() {}
}
