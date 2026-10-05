package ai.luumo.fractalstatus.model;

import ai.luumo.fractalstatus.model.config.DisplayColors;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Top-level wrapper for a monitoring config file.
 *
 * <p>This form lets colour schemes (and other global settings) be declared once
 * at the top of the file and referenced by name from any node:
 *
 * <pre>
 * {
 *   "schemes": {
 *     "node":  { "online": "#2E7D32", "warning": "#F9A825", "offline": "#C62828", "unknown": "#9E9E9E" },
 *     "group": { "online": "#ECEFF1", "warning": "#F9A825", "offline": "#C62828", "unknown": "#CFD8DC" }
 *   },
 *   "defaultSchemes": { "group": "group", "entity": "node" },
 *   "tree": { ... root node ... }
 * }
 * </pre>
 *
 * <p>A file whose top-level object is a node directly (no {@code tree} key) is
 * also accepted for backward compatibility; it simply carries no schemes.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class MonitoringConfig {

    /** Named colour schemes: scheme name -> per-state palette. */
    private Map<String, DisplayColors> schemes = new LinkedHashMap<>();

    /** Default scheme name per node type ("group" / "entity"). */
    private Map<String, String> defaultSchemes = new LinkedHashMap<>();

    /**
     * Named UI theme colours that are not per-node statuses (e.g. control-tile
     * colours). Opaque CSS colour strings; surfaced via {@code GET /api/theme}.
     */
    private Map<String, String> theme = new LinkedHashMap<>();

    /** The root node of the monitoring tree. */
    private Node tree;

    public Map<String, DisplayColors> getSchemes() {
        return schemes;
    }

    public void setSchemes(Map<String, DisplayColors> schemes) {
        this.schemes = schemes;
    }

    public Map<String, String> getDefaultSchemes() {
        return defaultSchemes;
    }

    public void setDefaultSchemes(Map<String, String> defaultSchemes) {
        this.defaultSchemes = defaultSchemes;
    }

    public Map<String, String> getTheme() {
        return theme;
    }

    public void setTheme(Map<String, String> theme) {
        this.theme = theme;
    }

    public Node getTree() {
        return tree;
    }

    public void setTree(Node tree) {
        this.tree = tree;
    }
}
