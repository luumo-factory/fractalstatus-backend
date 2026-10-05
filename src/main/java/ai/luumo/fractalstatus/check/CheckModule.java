package ai.luumo.fractalstatus.check;

import ai.luumo.fractalstatus.model.runtime.Metric;

import java.util.Map;

/**
 * A pluggable check type (ping, http, ...).
 *
 * <p>Implementations are stateless single-shot executors: the scheduler owns one
 * virtual thread per check instance and invokes {@link #check(CheckContext)}
 * once per interval. Implementations must therefore be thread-safe (ideally
 * holding no per-instance mutable state).
 */
public interface CheckModule {

    /** Stable module identifier referenced from config (e.g. "ping", "http"). */
    String id();

    /** Performs one check and returns its outcome. */
    CheckOutcome check(CheckContext context) throws Exception;

    /**
     * Derives this module's default primary display metric from a check's output,
     * or {@code null} if none is available (e.g. the check failed). Used to
     * populate an entity's {@code runtime.metric} unless overridden by
     * {@code display.metric} in the config.
     */
    default Metric primaryMetric(Map<String, Object> output) {
        return null;
    }
}
