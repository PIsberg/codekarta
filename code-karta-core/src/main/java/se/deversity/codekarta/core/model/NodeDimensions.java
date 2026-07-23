package se.deversity.codekarta.core.model;

import se.deversity.vibetags.annotations.AIImmutable;
import se.deversity.vibetags.annotations.AILocked;

@AILocked(reason = "Shared sizing constants consumed by all three tiers — SimpleLayoutEngine, ElkLayoutEngine, and SvgRenderer. Changing these values shifts node dimensions globally and will break layout tests.")
@AIImmutable(note = "Constants-only holder. No instance state may ever be added — this class is used across all three pipeline tiers.")
public final class NodeDimensions {

    public static final double DEFAULT_WIDTH  = 180.0;
    public static final double DEFAULT_HEIGHT = 70.0;

    private NodeDimensions() {}
}
