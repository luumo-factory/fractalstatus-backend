package ai.luumo.fractalstatus.log;

import com.fasterxml.jackson.annotation.JsonValue;

/**
 * Severity of a central-log entry.
 */
public enum LogLevel {
    DEBUG,
    INFO,
    WARN,
    ERROR;

    @JsonValue
    public String json() {
        return name().toLowerCase();
    }
}
