package ai.luumo.fractalstatus.model;

import ai.luumo.fractalstatus.model.runtime.GroupRuntime;
import ai.luumo.fractalstatus.view.Views;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * A group node: an organisational container with children. Its state is always
 * derived automatically as the worst state of its children (applied bottom-up),
 * so a failure anywhere in the subtree propagates up to every ancestor.
 *
 * <p>Groups may declare a {@link #getValues() values} map whose entries are
 * inherited by all descendant entity checks via {@code ${this.key}}
 * interpolation. Entity-level values take precedence over group values;
 * closer ancestors take precedence over more distant ones.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GroupNode extends Node {

    @JsonView({Views.Full.class, Views.Status.class})
    private GroupRuntime runtime = new GroupRuntime();

    /**
     * Shared values inherited by all descendant entity checks.
     * Absent/null when no group-level values are configured.
     */
    @JsonView({Views.ConfigWithDefaults.class, Views.Full.class})
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Map<String, Object> values = new LinkedHashMap<>();

    @JsonView(Views.Structure.class)
    private List<Node> children = new ArrayList<>();

    public GroupRuntime getRuntime() {
        return runtime;
    }

    public void setRuntime(GroupRuntime runtime) {
        this.runtime = runtime;
    }

    public Map<String, Object> getValues() {
        return values;
    }

    public void setValues(Map<String, Object> values) {
        this.values = values == null ? new LinkedHashMap<>() : values;
    }

    public List<Node> getChildren() {
        return children;
    }

    public void setChildren(List<Node> children) {
        this.children = children;
    }
}
