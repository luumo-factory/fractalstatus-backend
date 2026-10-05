package ai.luumo.fractalstatus.state;

import ai.luumo.fractalstatus.check.CheckModule;
import ai.luumo.fractalstatus.check.CheckModuleRegistry;
import ai.luumo.fractalstatus.model.EntityNode;
import ai.luumo.fractalstatus.model.config.CheckConfig;
import ai.luumo.fractalstatus.model.config.Display;
import ai.luumo.fractalstatus.model.config.MetricSpec;
import ai.luumo.fractalstatus.model.runtime.CheckResult;
import ai.luumo.fractalstatus.model.runtime.Metric;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Locale;

/**
 * Computes an entity's primary display {@link Metric} from its latest check
 * results.
 *
 * <p>Selection: the check named by {@code display.metric.check} (or the entity's
 * first check). Value: if {@code display.metric.field} is given, that output
 * field (formatted with {@code decimals}); otherwise the check module's default
 * {@link CheckModule#primaryMetric}. Returns {@code null} when no healthy value
 * is available, so the tile can fall back to status text.
 */
@Component
public class MetricResolver {

    private final CheckModuleRegistry registry;

    public MetricResolver(CheckModuleRegistry registry) {
        this.registry = registry;
    }

    public Metric resolve(EntityNode entity) {
        Display display = entity.getDisplay();
        MetricSpec spec = display != null ? display.getMetric() : null;
        List<CheckConfig> checks = entity.getConfig().getChecks();

        String checkName = (spec != null && StringUtils.hasText(spec.getCheck()))
                ? spec.getCheck()
                : (checks.isEmpty() ? null : checks.get(0).getName());
        if (checkName == null) {
            return null;
        }

        CheckResult result = entity.getRuntime().results().get(checkName);
        if (result == null) {
            return null;
        }

        // Explicit field override.
        if (spec != null && StringUtils.hasText(spec.getField())) {
            String value = format(result.getOutput().get(spec.getField()), spec.getDecimals());
            if (value == null) {
                return null;
            }
            return new Metric(value, spec.getUnit() != null ? spec.getUnit() : "");
        }

        // Module default metric.
        CheckConfig config = checks.stream()
                .filter(c -> checkName.equals(c.getName()))
                .findFirst()
                .orElse(null);
        if (config == null) {
            return null;
        }
        CheckModule module = registry.find(config.getModule()).orElse(null);
        if (module == null) {
            return null;
        }
        Metric metric = module.primaryMetric(result.getOutput());
        if (metric == null) {
            return null;
        }
        // Allow a unit override even when using the module's default value.
        if (spec != null && spec.getUnit() != null) {
            return new Metric(metric.value(), spec.getUnit());
        }
        return metric;
    }

    private String format(Object value, Integer decimals) {
        if (value == null) {
            return null;
        }
        if (value instanceof Number n && decimals != null) {
            return String.format(Locale.ROOT, "%." + decimals + "f", n.doubleValue());
        }
        return String.valueOf(value);
    }
}
