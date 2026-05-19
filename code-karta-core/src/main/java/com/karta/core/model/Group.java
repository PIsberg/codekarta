package com.karta.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import se.deversity.vibetags.annotations.AICore;
import se.deversity.vibetags.annotations.AISchemaSafe;
import se.deversity.vibetags.annotations.AIStrictTypes;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@AICore(sensitivity = "Medium", note = "IR cluster — maps a label (package, module boundary, try/catch region) to a set of node IDs. Used by the render tier to draw bounding frames.")
@AISchemaSafe
@AIStrictTypes
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Group {

    private String id;
    private String label;
    private List<String> memberIds;
    private Map<String, String> properties;

    public Group() {
        this.memberIds = new ArrayList<>();
        this.properties = new HashMap<>();
    }

    public Group(String id, String label) {
        this.id = id;
        this.label = label;
        this.memberIds = new ArrayList<>();
        this.properties = new HashMap<>();
    }

    public void addMember(String nodeId) {
        memberIds.add(nodeId);
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public List<String> getMemberIds() { return memberIds; }
    public void setMemberIds(List<String> memberIds) { this.memberIds = memberIds; }

    public Map<String, String> getProperties() { return properties; }
    public void setProperties(Map<String, String> properties) { this.properties = properties; }
}
