package ai.luumo.fractalstatus.model;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Health state of a node. UNKNOWN is the initial state before any check has
 * produced a result; the three "real" states are ONLINE, WARNING and OFFLINE.
 *
 * <p>The ordinal severity (via {@link #severity()}) is used when rolling state
 * up through organisational groups.
 */
public enum State {
    UNKNOWN("unknown", 0),
    ONLINE("online", 1),
    WARNING("warning", 2),
    OFFLINE("offline", 3);

    private final String json;
    private final int severity;

    State(String json, int severity) {
        this.json = json;
        this.severity = severity;
    }

    @JsonValue
    public String json() {
        return json;
    }

    public int severity() {
        return severity;
    }

    /** Returns the more severe of the two states. */
    public static State worst(State a, State b) {
        return a.severity >= b.severity ? a : b;
    }

    @JsonCreator
    public static State fromJson(String value) {
        for (State s : values()) {
            if (s.json.equalsIgnoreCase(value)) {
                return s;
            }
        }
        throw new IllegalArgumentException("Unknown state: " + value);
    }
}
