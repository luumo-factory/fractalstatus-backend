package ai.luumo.fractalstatus.scheduler;

import ai.luumo.fractalstatus.check.CheckModule;
import ai.luumo.fractalstatus.check.CheckModuleRegistry;
import ai.luumo.fractalstatus.check.CheckOutcome;
import ai.luumo.fractalstatus.model.GroupNode;
import ai.luumo.fractalstatus.interpolation.ValueInterpolator;
import ai.luumo.fractalstatus.log.LogLevel;
import ai.luumo.fractalstatus.log.LogStore;
import ai.luumo.fractalstatus.log.NodeLogger;
import ai.luumo.fractalstatus.model.EntityNode;
import ai.luumo.fractalstatus.model.config.CheckConfig;
import ai.luumo.fractalstatus.model.runtime.CheckResult;
import ai.luumo.fractalstatus.state.TreeStateUpdater;
import ai.luumo.fractalstatus.stats.CheckStats;
import ai.luumo.fractalstatus.tree.MonitoringTree;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Runs every configured check instance on its own virtual thread, once per
 * configured interval, updating runtime state and the central log.
 */
@Component
public class CheckScheduler {

    private static final Logger LOG = LoggerFactory.getLogger(CheckScheduler.class);
    private static final int DEFAULT_INTERVAL_SECONDS = 30;

    private final MonitoringTree tree;
    private final CheckModuleRegistry registry;
    private final TreeStateUpdater stateUpdater;
    private final LogStore logStore;
    private final CheckStats stats;

    private final List<Thread> threads = new CopyOnWriteArrayList<>();
    private volatile boolean running;

    public CheckScheduler(MonitoringTree tree,
                          CheckModuleRegistry registry,
                          TreeStateUpdater stateUpdater,
                          LogStore logStore,
                          CheckStats stats) {
        this.tree = tree;
        this.registry = registry;
        this.stateUpdater = stateUpdater;
        this.logStore = logStore;
        this.stats = stats;
    }

    /** Starts a virtual thread per check instance. Idempotent. */
    public synchronized void start() {
        if (running) {
            return;
        }
        running = true;
        int count = 0;
        for (EntityNode entity : tree.getEntities()) {
            for (CheckConfig check : entity.getConfig().getChecks()) {
                startCheck(entity, check);
                count++;
            }
        }
        LOG.info("CheckScheduler started {} check instance(s)", count);
        logStore.log(LogLevel.INFO, LogStore.SYSTEM_PATH,
                "scheduler started " + count + " check instance(s)");
    }

    /** Stops all check threads. */
    public synchronized void stop() {
        running = false;
        for (Thread t : threads) {
            t.interrupt();
        }
        threads.clear();
        logStore.log(LogLevel.INFO, LogStore.SYSTEM_PATH, "scheduler stopped");
    }

    public boolean isRunning() {
        return running;
    }

    private void startCheck(EntityNode entity, CheckConfig check) {
        CheckModule module = registry.find(check.getModule()).orElse(null);
        if (module == null) {
            logStore.log(LogLevel.ERROR, entity.getPath(),
                    "unknown check module '" + check.getModule()
                            + "' for check '" + check.getName() + "'");
            return;
        }

        Map<String, Object> resolvedConfig =
                ValueInterpolator.resolve(check.getConfig(), mergedValues(entity));
        int intervalSeconds = intervalSeconds(resolvedConfig);
        NodeLogger logger = new NodeLogger(logStore, entity.getPath());
        SimpleCheckContext ctx = new SimpleCheckContext(
                resolvedConfig, entity.getPath(), check.getName(), logger);

        Runnable loop = () -> runLoop(entity, check, module, ctx, intervalSeconds);
        Thread thread = Thread.ofVirtual()
                .name("check-" + entity.getPath() + "-" + check.getName())
                .start(loop);
        threads.add(thread);
    }

    private void runLoop(EntityNode entity, CheckConfig check, CheckModule module,
                         SimpleCheckContext ctx, int intervalSeconds) {
        while (running && !Thread.currentThread().isInterrupted()) {
            CheckResult result = new CheckResult(check.getName());
            result.setLastUpdate(Instant.now());
            try {
                CheckOutcome outcome = module.check(ctx);
                result.setOnline(outcome.online());
                LinkedHashMap<String, Object> output = new LinkedHashMap<>(outcome.output());
                // Always expose the primary online signal in the output map so
                // SpEL state rules can reference it as e.g. ping.online == false.
                output.put("online", outcome.online());
                result.setOutput(output);
                result.setMessage(outcome.message());
            } catch (Exception ex) {
                result.setOnline(false);
                result.setOutput(new LinkedHashMap<>());
                result.setMessage("check error: " + ex.getMessage());
                ctx.log().error("check '" + check.getName() + "' failed: " + ex.getMessage());
            }
            stats.record(check.getModule());
            entity.getRuntime().putResult(result);
            stateUpdater.onCheckUpdated(entity);

            try {
                Thread.sleep(intervalSeconds * 1000L);
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    /**
     * Merges values from the entity and all ancestor groups, with the entity's
     * own values taking highest priority and more distant ancestors lower priority.
     */
    private static Map<String, Object> mergedValues(EntityNode entity) {
        Map<String, Object> merged = new LinkedHashMap<>(entity.getConfig().getValues());
        GroupNode parent = entity.getParent();
        while (parent != null) {
            for (Map.Entry<String, Object> e : parent.getValues().entrySet()) {
                merged.putIfAbsent(e.getKey(), e.getValue());
            }
            parent = parent.getParent();
        }
        return merged;
    }

    private int intervalSeconds(Map<String, Object> config) {
        Object v = config.get("interval");
        if (v instanceof Number n && n.intValue() > 0) {
            return n.intValue();
        }
        if (v instanceof String s && !s.isBlank()) {
            try {
                int parsed = Integer.parseInt(s.trim());
                if (parsed > 0) {
                    return parsed;
                }
            } catch (NumberFormatException ignored) {
                // fall through to default
            }
        }
        return DEFAULT_INTERVAL_SECONDS;
    }
}
