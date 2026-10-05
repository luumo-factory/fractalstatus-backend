package ai.luumo.fractalstatus.model.runtime;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Ephemeral result of the most recent run of a single check instance.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CheckResult {

    private String name;
    private Instant lastUpdate;

    /** The module's primary health signal. */
    private boolean online;

    /** Structured, module-specific output (camelCase keys for SpEL access). */
    private Map<String, Object> output = new LinkedHashMap<>();

    /** Optional human-readable message. */
    private String message;

    public CheckResult() {
    }

    public CheckResult(String name) {
        this.name = name;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public Instant getLastUpdate() {
        return lastUpdate;
    }

    public void setLastUpdate(Instant lastUpdate) {
        this.lastUpdate = lastUpdate;
    }

    public boolean isOnline() {
        return online;
    }

    public void setOnline(boolean online) {
        this.online = online;
    }

    public Map<String, Object> getOutput() {
        return output;
    }

    public void setOutput(Map<String, Object> output) {
        this.output = output;
    }

    public String getMessage() {
        return message;
    }

    public void setMessage(String message) {
        this.message = message;
    }
}
