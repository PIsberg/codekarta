package se.deversity.codekarta.core.model;

import com.fasterxml.jackson.annotation.JsonInclude;
import org.jspecify.annotations.Nullable;
import se.deversity.vibetags.annotations.AICore;
import se.deversity.vibetags.annotations.AIDomainModel;
import se.deversity.vibetags.annotations.AISchemaSafe;
import se.deversity.vibetags.annotations.AIStrictTypes;

import java.util.HashMap;
import java.util.Map;

@AICore(sensitivity = "High", note = "IR vertex. Fields id/type/label/properties are the stable serialised schema; x/y/width/height are layout-only and may be null before Tier 2 runs.")
@AISchemaSafe
@AIStrictTypes
@AIDomainModel(allow = {"com.fasterxml.jackson.annotation.JsonInclude", "org.jspecify.annotations.Nullable"})
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Node {

    private String id;
    private String type;
    private String label;
    private Map<String, String> properties;

    // Populated by the layout tier
    private @Nullable Double x;
    private @Nullable Double y;
    private @Nullable Double width;
    private @Nullable Double height;

    public Node() {}

    public Node(String id, String type, String label) {
        this.id = id;
        this.type = type;
        this.label = label;
        this.properties = new HashMap<>();
    }

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public String getLabel() { return label; }
    public void setLabel(String label) { this.label = label; }

    public Map<String, String> getProperties() { return properties; }
    public void setProperties(Map<String, String> properties) { this.properties = properties; }

    public @Nullable Double getX() { return x; }
    public void setX(@Nullable Double x) { this.x = x; }

    public @Nullable Double getY() { return y; }
    public void setY(@Nullable Double y) { this.y = y; }

    public @Nullable Double getWidth() { return width; }
    public void setWidth(@Nullable Double width) { this.width = width; }

    public @Nullable Double getHeight() { return height; }
    public void setHeight(@Nullable Double height) { this.height = height; }
}
