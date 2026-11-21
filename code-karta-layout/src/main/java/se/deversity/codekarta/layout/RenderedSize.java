package se.deversity.codekarta.layout;

import se.deversity.codekarta.core.model.Node;
import se.deversity.codekarta.core.model.NodeDimensions;
import se.deversity.vibetags.annotations.AIKeepInSync;

import java.util.Map;

/**
 * How tall SvgRenderer will draw a node, for the layout engines to reserve that much room.
 *
 * <p>SvgRenderer draws a class or interface with {@code fields} or {@code methods} properties at
 * the height its compartments need, whatever height the layout assigned. An engine that reserves
 * less gets boxes drawn on top of each other: {@code --layout elk} reserved
 * {@link NodeDimensions#DEFAULT_HEIGHT} for boxes drawn 505px tall (issue #230). Both engines call
 * this one estimate, so there is one copy of the formula in the layout tier rather than one per
 * engine.
 */
final class RenderedSize {

    private static final double COMPARTMENT_HEADER_H  = 44.0;
    private static final double COMPARTMENT_SECTION_H = 13.0; // padding * 2 + the divider line
    private static final double COMPARTMENT_LINE_H    = 15.0;

    private RenderedSize() {
    }

    /**
     * The rendered height of {@code node}: the compartment height for a node with members,
     * otherwise {@link NodeDimensions#DEFAULT_HEIGHT}. Never less than the default.
     */
    @AIKeepInSync(
        mirrors = "se.deversity.codekarta.render.SvgRenderer#computeCompartmentHeight",
        reason = "Layout may not reference render, so the compartment-height formula exists twice. If SvgRenderer draws compartments taller than this estimate, both layout engines leave too little room and class boxes overlap; if shorter, diagrams grow gaps.",
        enforcedBy = "RenderedOverlapTest.theEstimateMatchesWhatSvgRendererDraws, against heights measured from SvgRenderer output; no test compares the two formulas directly")
    static double estimateHeight(Node node) {
        Map<String, String> props = node.getProperties();
        if (props == null) return NodeDimensions.DEFAULT_HEIGHT;
        String fields  = props.get("fields");
        String methods = props.get("methods");
        if ((fields == null || fields.isEmpty()) && (methods == null || methods.isEmpty())) {
            return NodeDimensions.DEFAULT_HEIGHT;
        }
        double h = COMPARTMENT_HEADER_H;
        if (fields != null && !fields.isEmpty()) {
            h += COMPARTMENT_SECTION_H + fields.split("\n").length * COMPARTMENT_LINE_H;
        }
        if (methods != null && !methods.isEmpty()) {
            h += COMPARTMENT_SECTION_H + methods.split("\n").length * COMPARTMENT_LINE_H;
        }
        return Math.max(h, NodeDimensions.DEFAULT_HEIGHT);
    }
}
