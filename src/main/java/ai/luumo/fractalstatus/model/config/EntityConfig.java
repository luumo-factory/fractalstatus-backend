package ai.luumo.fractalstatus.model.config;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Static configuration for an {@code entity} node.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EntityConfig {

    /** Named values; targets of {@code ${this.<key>}} interpolation. */
    private Map<String, Object> values = new LinkedHashMap<>();

    /** Check instances to run against this entity. */
    private List<CheckConfig> checks = new ArrayList<>();

    /** Rules mapping check output to the entity state. */
    private StateRules state;

    public Map<String, Object> getValues() {
        return values;
    }

    public void setValues(Map<String, Object> values) {
        this.values = values;
    }

    public List<CheckConfig> getChecks() {
        return checks;
    }

    public void setChecks(List<CheckConfig> checks) {
        this.checks = checks;
    }

    public StateRules getState() {
        return state;
    }

    public void setState(StateRules state) {
        this.state = state;
    }
}
