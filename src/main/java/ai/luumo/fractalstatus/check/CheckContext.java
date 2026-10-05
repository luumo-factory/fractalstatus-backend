package ai.luumo.fractalstatus.check;

import ai.luumo.fractalstatus.log.NodeLogger;

import java.util.Map;

/**
 * Everything a {@link CheckModule} needs to perform one execution.
 */
public interface CheckContext {

    /** Resolved configuration (after {@code ${this.*}} interpolation). */
    Map<String, Object> config();

    /** The dotted path of the entity being checked (for logging/identity). */
    String nodePath();

    /** The name of this check instance within the entity. */
    String checkName();

    /** Path-scoped logger. */
    NodeLogger log();

    /** Convenience typed accessors over {@link #config()}. */
    default String getString(String key, String defaultValue) {
        Object v = config().get(key);
        return v == null ? defaultValue : String.valueOf(v);
    }

    default int getInt(String key, int defaultValue) {
        Object v = config().get(key);
        if (v instanceof Number n) {
            return n.intValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            return Integer.parseInt(s.trim());
        }
        return defaultValue;
    }
}
