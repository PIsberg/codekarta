package com.karta.core.model;

import se.deversity.vibetags.annotations.AILocked;

@AILocked(reason = "String values are matched by identity in SvgRenderer's CSS class-name map and in all four parsers. Renaming or adding a constant requires updating SvgRenderer, all parser switch/if chains, and integration tests simultaneously.")
public final class NodeType {
    public static final String CLASS     = "CLASS";
    public static final String INTERFACE = "INTERFACE";
    public static final String METHOD    = "METHOD";
    public static final String MODULE    = "MODULE";
    public static final String PACKAGE   = "PACKAGE";
    public static final String EXCEPTION = "EXCEPTION";

    private NodeType() {}
}
