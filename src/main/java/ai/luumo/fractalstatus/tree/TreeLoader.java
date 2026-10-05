package ai.luumo.fractalstatus.tree;

import ai.luumo.fractalstatus.FractalstatusProperties;
import ai.luumo.fractalstatus.model.EntityNode;
import ai.luumo.fractalstatus.model.GroupNode;
import ai.luumo.fractalstatus.model.MonitoringConfig;
import ai.luumo.fractalstatus.model.Node;
import ai.luumo.fractalstatus.model.config.CheckConfig;
import ai.luumo.fractalstatus.model.config.DisplayColors;
import ai.luumo.fractalstatus.model.config.StateRules;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.util.List;
import java.util.stream.Collectors;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;
import java.util.Map;

/**
 * Loads the monitoring config from the configured resource, builds the typed
 * tree, assigns path keys and parent links, and installs it into
 * {@link MonitoringTree}.
 */
@Component
public class TreeLoader {

    private final FractalstatusProperties properties;
    private final ResourceLoader resourceLoader;
    private final ObjectMapper objectMapper;
    private final MonitoringTree tree;
    private final SchemeResolver schemeResolver;

    public TreeLoader(FractalstatusProperties properties,
                      ResourceLoader resourceLoader,
                      ObjectMapper objectMapper,
                      MonitoringTree tree,
                      SchemeResolver schemeResolver) {
        this.properties = properties;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
        this.tree = tree;
        this.schemeResolver = schemeResolver;
    }

    /** Reads and installs the tree. */
    public void load() throws IOException {
        Resource resource = resourceLoader.getResource(properties.getConfigPath());
        if (!resource.exists()) {
            throw new IOException("Config resource not found: " + properties.getConfigPath());
        }
        JsonNode raw;
        try (InputStream in = resource.getInputStream()) {
            raw = objectMapper.readTree(in);
        }

        Node root;
        Map<String, DisplayColors> schemes;
        Map<String, String> defaultSchemes;
        Map<String, String> theme;
        if (raw.has("tree")) {
            // Wrapper form: { schemes, defaultSchemes, theme, tree }.
            MonitoringConfig config = objectMapper.treeToValue(raw, MonitoringConfig.class);
            root = config.getTree();
            schemes = config.getSchemes();
            defaultSchemes = config.getDefaultSchemes();
            theme = config.getTheme();
        } else {
            // Backward-compatible bare-node form.
            root = objectMapper.treeToValue(raw, Node.class);
            schemes = Map.of();
            defaultSchemes = Map.of();
            theme = Map.of();
        }

        assignPaths(root, null, null);
        applyDefaultStateRules(root);
        schemeResolver.resolve(root, schemes, defaultSchemes);
        tree.install(root, raw, schemes, theme);
    }

    private void assignPaths(Node node, String parentPath, GroupNode parent) {
        String path = parentPath == null ? node.getId() : parentPath + "." + node.getId();
        node.setPath(path);
        node.setParent(parent);
        if (node instanceof GroupNode g) {
            for (Node child : g.getChildren()) {
                assignPaths(child, path, g);
            }
        }
    }

    /**
     * Walks the tree and fills in a default {@link StateRules} for any entity
     * that has none configured. The default rule marks the entity offline when
     * any of its checks reports {@code online == false}, mirroring the previous
     * hard-coded fallback but now as an explicit, inspectable SpEL expression.
     *
     * <p>This means {@code /api/tree/config-defaults} always shows effective
     * state rules, giving users a concrete starting point for customisation.
     */
    private void applyDefaultStateRules(Node node) {
        if (node instanceof EntityNode entity) {
            if (entity.getConfig().getState() == null) {
                entity.getConfig().setState(buildDefault(entity));
            }
        }
        if (node instanceof GroupNode g) {
            for (Node child : g.getChildren()) {
                applyDefaultStateRules(child);
            }
        }
    }

    private static StateRules buildDefault(EntityNode entity) {
        List<CheckConfig> checks = entity.getConfig().getChecks();
        if (checks.isEmpty()) {
            return null;
        }
        String offlineWhen = checks.stream()
                .map(c -> c.getName() + ".online == false")
                .collect(java.util.stream.Collectors.joining(" or "));
        StateRules rules = new StateRules();
        rules.setOfflineWhen(offlineWhen);
        return rules; // fallback defaults to ONLINE inside StateRules
    }
}
