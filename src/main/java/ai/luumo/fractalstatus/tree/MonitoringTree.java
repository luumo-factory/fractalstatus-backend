package ai.luumo.fractalstatus.tree;

import ai.luumo.fractalstatus.model.EntityNode;
import ai.luumo.fractalstatus.model.GroupNode;
import ai.luumo.fractalstatus.model.Node;
import ai.luumo.fractalstatus.model.config.DisplayColors;
import tools.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Holds the live, in-memory monitoring tree plus the raw (as-supplied) config.
 *
 * <p>The resolved {@link #getRoot() typed tree} carries both config and runtime
 * state and backs API endpoints #2-#4; the {@link #getRaw() raw JSON} backs
 * endpoint #1 (config exactly as supplied).
 */
@Component
public class MonitoringTree {

    private volatile Node root;
    private volatile Node visibleRoot;
    private volatile JsonNode raw;
    private volatile List<EntityNode> entities = List.of();
    private volatile Map<String, DisplayColors> schemes = Map.of();
    private volatile Map<String, String> theme = Map.of();
    private final Map<String, Node> byPath = new ConcurrentHashMap<>();

    /** Installs a freshly loaded tree. */
    public synchronized void install(Node root, JsonNode raw,
                                     Map<String, DisplayColors> schemes,
                                     Map<String, String> theme) {
        this.root = root;
        this.raw = raw;
        this.schemes = schemes == null ? Map.of() : Map.copyOf(schemes);
        this.theme = theme == null ? Map.of() : Map.copyOf(theme);
        List<EntityNode> collected = new ArrayList<>();
        byPath.clear();
        index(root, collected);
        this.entities = List.copyOf(collected);
        this.visibleRoot = filterHidden(root);
    }

    private void index(Node node, List<EntityNode> entities) {
        if (node == null) {
            return;
        }
        if (node.getPath() != null) {
            byPath.put(node.getPath(), node);
        }
        // Disabled entities are indexed (for state lookups) but not scheduled.
        if (node instanceof EntityNode e && !e.isDisabled()) {
            entities.add(e);
        }
        if (node instanceof GroupNode g) {
            for (Node child : g.getChildren()) {
                index(child, entities);
            }
        }
    }

    /**
     * Recursively removes hidden nodes from the tree, creating minimal GroupNode
     * copies where necessary. Entity nodes and non-hidden groups are returned
     * as-is (same references, same live runtime state).
     */
    private static Node filterHidden(Node node) {
        if (node == null || node.isHidden()) {
            return null;
        }
        if (!(node instanceof GroupNode group)) {
            return node; // EntityNode - not hidden, return as-is
        }
        boolean anyChanged = false;
        List<Node> filteredChildren = new ArrayList<>();
        for (Node child : group.getChildren()) {
            Node filtered = filterHidden(child);
            if (filtered == null) {
                anyChanged = true; // child removed
            } else {
                filteredChildren.add(filtered);
                if (filtered != child) anyChanged = true; // child replaced with filtered copy
            }
        }
        if (!anyChanged) {
            return group; // nothing changed anywhere in this subtree
        }
        // One or more children were hidden - create a shallow copy with the
        // filtered list. All other references (runtime, display, etc.) are shared.
        GroupNode copy = new GroupNode();
        copy.setId(group.getId());
        copy.setName(group.getName());
        copy.setType(group.getType());
        copy.setPath(group.getPath());
        copy.setDisplay(group.getDisplay());
        copy.setParent(group.getParent());
        copy.setRuntime(group.getRuntime());
        copy.setChildren(filteredChildren);
        return copy;
    }

    public Node getRoot() {
        return root;
    }

    /** Tree with hidden nodes removed, suitable for API responses. */
    public Node getVisibleRoot() {
        return visibleRoot;
    }

    public JsonNode getRaw() {
        return raw;
    }

    /** Named colour schemes declared at the top of the config. */
    public Map<String, DisplayColors> getSchemes() {
        return schemes;
    }

    /** Named UI theme colours (e.g. control-tile colours). */
    public Map<String, String> getTheme() {
        return theme;
    }

    /** All entity nodes in the tree. */
    public List<EntityNode> getEntities() {
        return entities;
    }

    public Node findByPath(String path) {
        return byPath.get(path);
    }

    public boolean isLoaded() {
        return root != null;
    }
}
