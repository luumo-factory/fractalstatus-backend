package ai.luumo.fractalstatus.model.runtime;

import ai.luumo.fractalstatus.model.State;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Ephemeral runtime state for an entity. Updated concurrently by the check
 * scheduler (one virtual thread per check instance), so check results are held
 * in a concurrent map keyed by check name and the state is volatile.
 */
public class EntityRuntime {

    private volatile State state = State.UNKNOWN;

    private volatile Metric metric;

    private final Map<String, CheckResult> checks = new ConcurrentHashMap<>();

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }

    /** Primary display metric for the entity tile; omitted when unavailable. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public Metric getMetric() {
        return metric;
    }

    public void setMetric(Metric metric) {
        this.metric = metric;
    }

    /** Records/replaces the latest result for a check. */
    public void putResult(CheckResult result) {
        checks.put(result.getName(), result);
    }

    /** Live view of results, keyed by check name. */
    public Map<String, CheckResult> results() {
        return checks;
    }

    /** Serialized form: results as a list (ordering not guaranteed). */
    @JsonProperty("checks")
    public List<CheckResult> getChecks() {
        return new ArrayList<>(checks.values());
    }
}
