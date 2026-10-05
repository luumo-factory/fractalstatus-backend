package ai.luumo.fractalstatus.api;

import ai.luumo.fractalstatus.tree.MonitoringTree;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Exposes the named UI theme colours declared at the top of the config
 * ({@code theme} block). These are opaque CSS colour strings for UI chrome that
 * are not per-node statuses (e.g. control-tile colours).
 *
 * <p>Static for the life of a loaded config, so the SPA can fetch it once.
 */
@RestController
@RequestMapping("/api/theme")
public class ThemeController {

    private final MonitoringTree tree;

    public ThemeController(MonitoringTree tree) {
        this.tree = tree;
    }

    /** Returns the theme colour map, e.g. {@code { "explode": "oklch(...)", "collapse": "oklch(...)" }}. */
    @GetMapping
    public Map<String, String> theme() {
        return tree.getTheme();
    }
}
