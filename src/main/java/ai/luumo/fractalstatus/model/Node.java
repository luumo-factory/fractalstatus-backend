package ai.luumo.fractalstatus.model;

import ai.luumo.fractalstatus.model.config.Display;
import ai.luumo.fractalstatus.view.Views;
import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.annotation.JsonView;

/**
 * Base type for every node in the monitoring tree.
 *
 * <p>Polymorphism is driven by the {@code type} property ("group" / "entity"),
 * which is also a first-class field so it round-trips through the API.
 *
 * <p>Node identifiers ({@link #getId()}) must be valid SpEL property names
 * ({@code [A-Za-z_][A-Za-z0-9_]*}). The dotted {@link #getPath() path} built
 * from ids (e.g. {@code root.servers.pveHosts.pve01}) is therefore also a valid
 * SpEL chain and is used as the key for log filtering.
 */
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        include = JsonTypeInfo.As.EXISTING_PROPERTY,
        property = "type",
        visible = true)
@JsonSubTypes({
        @JsonSubTypes.Type(value = GroupNode.class, name = "group"),
        @JsonSubTypes.Type(value = EntityNode.class, name = "entity")
})
public abstract class Node {

    @JsonView(Views.Structure.class)
    private String id;

    @JsonView(Views.Structure.class)
    private String name;

    @JsonView(Views.Structure.class)
    private NodeType type;

    /** Fully-qualified dotted path, assigned at load time; not part of the config file. */
    @JsonView(Views.Structure.class)
    private String path;

    /**
     * Layout / presentation metadata. Shared by all node types and included in
     * the config-defaults, full and status views (it is layout, not heavy
     * config).
     */
    @JsonView(Views.Structure.class)
    private Display display = new Display();

    /**
     * When {@code true} the scheduler skips this node and runs no checks against
     * it. Disabled nodes retain their position in the tree and still affect
     * group rollup unless also hidden.
     */
    @JsonView(Views.Structure.class)
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean disabled;

    /**
     * When {@code true} the node is excluded from all API responses and is
     * invisible to the UI. Hidden nodes do not participate in group rollup.
     * Typically set alongside {@code disabled}.
     */
    @JsonView(Views.Structure.class)
    @JsonInclude(JsonInclude.Include.NON_DEFAULT)
    private boolean hidden;

    /** Parent link for roll-up; never serialized. */
    @JsonIgnore
    private GroupNode parent;

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public NodeType getType() {
        return type;
    }

    public void setType(NodeType type) {
        this.type = type;
    }

    public String getPath() {
        return path;
    }

    public void setPath(String path) {
        this.path = path;
    }

    public Display getDisplay() {
        return display;
    }

    public void setDisplay(Display display) {
        this.display = display;
    }

    @JsonIgnore
    public boolean isDisabled() {
        return disabled;
    }

    public void setDisabled(boolean disabled) {
        this.disabled = disabled;
    }

    public boolean isHidden() {
        return hidden;
    }

    public void setHidden(boolean hidden) {
        this.hidden = hidden;
    }

    public GroupNode getParent() {
        return parent;
    }

    public void setParent(GroupNode parent) {
        this.parent = parent;
    }
}
