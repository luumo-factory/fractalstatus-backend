package ai.luumo.fractalstatus.model.config;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Declares a single check instance on an entity.
 *
 * <p>{@link #getConfig() config} values may contain {@code ${this.<key>}}
 * placeholders which are resolved against the owning entity's values when the
 * live check instance is built (see the interpolation package). The values
 * stored here remain the original, un-interpolated templates.
 */
public class CheckConfig {

    /** Unique name within the entity; valid SpEL identifier. */
    private String name;

    /** Which {@code CheckModule} implementation to run. */
    private String module;

    /** Module-specific configuration (may contain ${this.*} placeholders). */
    private Map<String, Object> config = new LinkedHashMap<>();

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getModule() {
        return module;
    }

    public void setModule(String module) {
        this.module = module;
    }

    public Map<String, Object> getConfig() {
        return config;
    }

    public void setConfig(Map<String, Object> config) {
        this.config = config;
    }
}
