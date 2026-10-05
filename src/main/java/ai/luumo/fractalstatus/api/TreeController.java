package ai.luumo.fractalstatus.api;

import ai.luumo.fractalstatus.model.Node;
import ai.luumo.fractalstatus.tree.MonitoringTree;
import ai.luumo.fractalstatus.view.Views;
import com.fasterxml.jackson.annotation.JsonView;
import tools.jackson.databind.JsonNode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The four tree views:
 * <ol>
 *   <li>{@code GET /api/tree/config} - config exactly as supplied (raw).</li>
 *   <li>{@code GET /api/tree/config-defaults} - resolved config incl. defaults.</li>
 *   <li>{@code GET /api/tree/full} - config + defaults + runtime.</li>
 *   <li>{@code GET /api/tree/status} - tree + runtime only.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/tree")
public class TreeController {

    private final MonitoringTree tree;

    public TreeController(MonitoringTree tree) {
        this.tree = tree;
    }

    /** #1 Config exactly as supplied (served from the retained raw JSON). */
    @GetMapping("/config")
    public ResponseEntity<JsonNode> config() {
        JsonNode raw = tree.getRaw();
        return raw == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(raw);
    }

    /** #2 Resolved config including materialised defaults. */
    @GetMapping("/config-defaults")
    @JsonView(Views.ConfigWithDefaults.class)
    public ResponseEntity<Node> configWithDefaults() {
        return root();
    }

    /** #3 Full: config + defaults + runtime. */
    @GetMapping("/full")
    @JsonView(Views.Full.class)
    public ResponseEntity<Node> full() {
        return root();
    }

    /** #4 Status page view: structure + runtime only. */
    @GetMapping("/status")
    @JsonView(Views.Status.class)
    public ResponseEntity<Node> status() {
        return root();
    }

    private ResponseEntity<Node> root() {
        Node root = tree.getVisibleRoot();
        return root == null ? ResponseEntity.noContent().build() : ResponseEntity.ok(root);
    }
}
