package ai.luumo.fractalstatus.model;

import ai.luumo.fractalstatus.model.config.EntityConfig;
import ai.luumo.fractalstatus.model.runtime.EntityRuntime;
import ai.luumo.fractalstatus.view.Views;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonView;

/**
 * An entity node: a monitored thing with values, checks and state rules.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EntityNode extends Node {

    @JsonView({Views.ConfigWithDefaults.class, Views.Full.class})
    private EntityConfig config = new EntityConfig();

    @JsonView({Views.Full.class, Views.Status.class})
    private EntityRuntime runtime = new EntityRuntime();

    public EntityConfig getConfig() {
        return config;
    }

    public void setConfig(EntityConfig config) {
        this.config = config;
    }

    public EntityRuntime getRuntime() {
        return runtime;
    }

    public void setRuntime(EntityRuntime runtime) {
        this.runtime = runtime;
    }
}
