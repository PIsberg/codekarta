package com.karta.core.model;

import se.deversity.vibetags.annotations.AILocked;

@AILocked(reason = "String values are used as CSS class selectors in SvgRenderer and as edge-type identifiers emitted by all parsers. Adding, removing, or renaming a constant requires coordinated changes across the entire pipeline.")
public final class EdgeType {
    public static final String CALLS                 = "CALLS";
    public static final String EXTENDS               = "EXTENDS";
    public static final String IMPLEMENTS            = "IMPLEMENTS";
    public static final String REQUIRES              = "REQUIRES";
    public static final String EXPORTS               = "EXPORTS";
    public static final String HAS                   = "HAS";
    public static final String EXCEPTION_PROPAGATION = "EXCEPTION_PROPAGATION";

    private EdgeType() {}
}
