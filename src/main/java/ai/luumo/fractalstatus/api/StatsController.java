package ai.luumo.fractalstatus.api;

import ai.luumo.fractalstatus.stats.CheckStats;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.lang.management.ManagementFactory;
import java.lang.management.MemoryUsage;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Exposes check execution rate statistics.
 *
 * <p>{@code GET /api/stats} returns the number of check executions per module
 * in the last 60 seconds (equivalent to tests per minute) plus the combined
 * total across all modules.
 *
 * <p>Example response:
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
 */
@RestController
@RequestMapping("/api")
public class StatsController {

    private final CheckStats stats;

    public StatsController(CheckStats stats) {
        this.stats = stats;
    }

    @GetMapping("/stats")
    public Map<String, Object> stats() {
        Map<String, Object> result = new LinkedHashMap<>(stats.snapshot());
        result.put("jvm", jvmInfo());
        return result;
    }

    private static Map<String, Object> jvmInfo() {
        var runtime = ManagementFactory.getRuntimeMXBean();
        var memory  = ManagementFactory.getMemoryMXBean();

        MemoryUsage heap    = memory.getHeapMemoryUsage();
        MemoryUsage nonHeap = memory.getNonHeapMemoryUsage();

        Map<String, Object> heapMap = new LinkedHashMap<>();
        heapMap.put("usedMb",      toMb(heap.getUsed()));
        heapMap.put("committedMb", toMb(heap.getCommitted()));
        heapMap.put("maxMb",       heap.getMax() < 0 ? null : toMb(heap.getMax()));

        Map<String, Object> jvm = new LinkedHashMap<>();
        jvm.put("version",        System.getProperty("java.version"));
        jvm.put("vendor",         System.getProperty("java.vendor"));
        jvm.put("uptimeSeconds",  runtime.getUptime() / 1000);
        jvm.put("heap",           heapMap);
        jvm.put("nonHeapUsedMb", nonHeap.getUsed() < 0 ? null : toMb(nonHeap.getUsed()));
        return jvm;
    }

    private static double toMb(long bytes) {
        return Math.round(bytes / 1_048_576.0 * 10.0) / 10.0;
    }
}
