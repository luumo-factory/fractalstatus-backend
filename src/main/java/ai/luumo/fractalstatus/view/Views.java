package ai.luumo.fractalstatus.view;

/**
 * Jackson serialization views backing the tree API endpoints.
 *
 * <ul>
 *   <li>{@link ConfigWithDefaults} - resolved config including materialised
 *       defaults (endpoint #2).</li>
 *   <li>{@link Full} - config + defaults + runtime state (endpoint #3).</li>
 *   <li>{@link Status} - tree structure + runtime only, omitting config not
 *       required to render the status page (endpoint #4).</li>
 * </ul>
 *
 * <p>Endpoint #1 ("config exactly as supplied") is served from the retained raw
 * JSON tree and does not use a view.
 */
public final class Views {

    private Views() {
    }

    /** Structural fields shared by every view (id, name, type, path, children). */
    public static class Structure {
    }

    /** Endpoint #2: resolved config with defaults. */
    public static class ConfigWithDefaults extends Structure {
    }

    /** Endpoint #3: full config + runtime. */
    public static class Full extends Structure {
    }

    /** Endpoint #4: status page view (structure + runtime). */
    public static class Status extends Structure {
    }
}
