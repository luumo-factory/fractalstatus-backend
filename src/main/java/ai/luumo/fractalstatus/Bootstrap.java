package ai.luumo.fractalstatus;

import ai.luumo.fractalstatus.log.LogLevel;
import ai.luumo.fractalstatus.log.LogStore;
import ai.luumo.fractalstatus.scheduler.CheckScheduler;
import ai.luumo.fractalstatus.tree.TreeLoader;
import jakarta.annotation.PreDestroy;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Loads the monitoring tree on startup and (optionally) starts the scheduler.
 */
@Component
public class Bootstrap implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(Bootstrap.class);

    private final TreeLoader treeLoader;
    private final CheckScheduler scheduler;
    private final FractalstatusProperties properties;
    private final LogStore logStore;

    public Bootstrap(TreeLoader treeLoader,
                     CheckScheduler scheduler,
                     FractalstatusProperties properties,
                     LogStore logStore) {
        this.treeLoader = treeLoader;
        this.scheduler = scheduler;
        this.properties = properties;
        this.logStore = logStore;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        treeLoader.load();
        logStore.log(LogLevel.INFO, LogStore.SYSTEM_PATH,
                "config loaded from " + properties.getConfigPath());
        LOG.info("Monitoring tree loaded from {}", properties.getConfigPath());
        if (properties.isAutoStart()) {
            scheduler.start();
        }
    }

    @PreDestroy
    public void shutdown() {
        scheduler.stop();
    }
}
