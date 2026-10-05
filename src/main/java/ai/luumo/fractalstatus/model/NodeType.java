package ai.luumo.fractalstatus.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * The kind of node in the monitoring tree.
 */
public enum NodeType {
    GROUP("group"),
    ENTITY("entity");

    private final String json;

    NodeType(String json) {
        this.json = json;
    }

    @JsonValue
    public String json() {
        return json;
    }

    @JsonCreator
    public static NodeType fromJson(String value) {
        for (NodeType t : values()) {
            if (t.json.equalsIgnoreCase(value)) {
                return t;
            }
        }
        throw new IllegalArgumentException("Unknown node type: " + value);
    }
}
