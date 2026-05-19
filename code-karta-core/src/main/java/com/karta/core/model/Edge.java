package com.karta.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import se.deversity.vibetags.annotations.AICore;
import se.deversity.vibetags.annotations.AISchemaSafe;
import se.deversity.vibetags.annotations.AIStrictTypes;

@AICore(sensitivity = "High", note = "IR directed edge. id/sourceId/targetId/type are required; label is optional (sequence number for CALLS, field name for HAS).")
@AISchemaSafe
@AIStrictTypes
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Edge {

    private String id;
    private String sourceId;
    private String targetId;
    private String type;
    private String label;

    public Edge() {}

    public Edge(String id, String sourceId, String targetId, String type) {
        this.id = id;
        this.sourceId = sourceId;
        this.targetId = targetId;
        this.type = type;
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getSourceId() { return sourceId; }
    public void setSourceId(String sourceId) { this.sourceId = sourceId; }

    public String getTargetId() { return targetId; }
    public void setTargetId(String targetId) { this.targetId = targetId; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }
}
