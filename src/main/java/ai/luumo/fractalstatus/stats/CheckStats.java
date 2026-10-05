package ai.luumo.fractalstatus.stats;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

/**
 * Tracks check execution rates using a per-module sliding time window.
 *
 * <p>Each call to {@link #record(String)} appends the current timestamp to
 * a per-module {@link ConcurrentLinkedQueue}. On each {@link #snapshot()}
 * call, entries older than {@link #WINDOW_SECONDS} are pruned; the remaining
 * count is the number of executions in the last minute, i.e. the tests-per-minute
 * rate.
 *
 * <p>Thread-safe: recording and snapshotting are safe to call from any number
 * of concurrent check threads.
 */
@Component
public class CheckStats {

    static final long WINDOW_SECONDS = 60;

    /** Per-module queue of recent execution timestamps. */
    private final ConcurrentHashMap<String, Queue<Instant>> windows =
            new ConcurrentHashMap<>();

    /**
     * Records one check execution for the given module.
     * Called from the scheduler's run loop after every check attempt.
     *
     * @param moduleId the {@link ai.luumo.fractalstatus.check.CheckModule#id()} value,
     *                 e.g. {@code "ping"}, {@code "http"}, {@code "mqtt"}
     */
    public void record(String moduleId) {
        windows.computeIfAbsent(moduleId, k -> new ConcurrentLinkedQueue<>())
               .offer(Instant.now());
    }

    /**
     * Returns the current stats snapshot.
     *
     * <p>The returned map has the following structure:
     * <pre>
     * {
     *   "windowSeconds": 60,
     *   "modules": {
     *     "http":      12,
     *     "http-json":  4,
     *     "mqtt":      25,
     *     "ping":      80,
     *     "webhook":    2
     *   },
     *   "total": 123
     * }
     * </pre>
     * Values are counts of executions within the last {@value #WINDOW_SECONDS} seconds,
     * which equals the tests-per-minute rate.
     */
    public Map<String, Object> snapshot() {
        Instant cutoff = Instant.now().minusSeconds(WINDOW_SECONDS);

        List<String> moduleIds = new ArrayList<>(windows.keySet());
        moduleIds.sort(String::compareTo);

        Map<String, Integer> perModule = new LinkedHashMap<>();
        int total = 0;

        for (String moduleId : moduleIds) {
            Queue<Instant> q = windows.get(moduleId);
            if (q == null) {
                continue;
            }
            // Remove expired entries. ConcurrentLinkedQueue.removeIf is safe
            // under concurrent offer() calls from check threads.
            q.removeIf(t -> t.isBefore(cutoff));
            int count = q.size();
            perModule.put(moduleId, count);
            total += count;
        }

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("windowSeconds", WINDOW_SECONDS);
        result.put("modules", perModule);
        result.put("total", total);
        return result;
    }
}
