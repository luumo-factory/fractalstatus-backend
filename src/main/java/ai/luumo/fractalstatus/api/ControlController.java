package ai.luumo.fractalstatus.api;

import ai.luumo.fractalstatus.scheduler.CheckScheduler;
import ai.luumo.fractalstatus.tree.TreeLoader;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.Map;

/**
 * Operational control: scheduler start/stop and config reload.
 */
@RestController
@RequestMapping("/api")
public class ControlController {

    private final CheckScheduler scheduler;
    private final TreeLoader treeLoader;

    public ControlController(CheckScheduler scheduler, TreeLoader treeLoader) {
        this.scheduler = scheduler;
        this.treeLoader = treeLoader;
    }

    @GetMapping("/scheduler")
    public Map<String, Object> schedulerStatus() {
        return Map.of("running", scheduler.isRunning());
    }

    @PostMapping("/scheduler/start")
    public Map<String, Object> start() {
        scheduler.start();
        return Map.of("running", scheduler.isRunning());
    }

    @PostMapping("/scheduler/stop")
    public Map<String, Object> stop() {
        scheduler.stop();
        return Map.of("running", scheduler.isRunning());
    }

    /** Stops the scheduler, reloads config from disk, and restarts it. */
    @PostMapping("/reload")
    public Map<String, Object> reload() throws IOException {
        boolean wasRunning = scheduler.isRunning();
        scheduler.stop();
        treeLoader.load();
        if (wasRunning) {
            scheduler.start();
        }
        return Map.of("reloaded", true, "running", scheduler.isRunning());
    }
}
