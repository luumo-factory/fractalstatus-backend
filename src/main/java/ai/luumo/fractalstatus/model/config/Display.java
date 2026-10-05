package ai.luumo.fractalstatus.model.config;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Layout / presentation metadata shared by every node (group and entity).
 *
 * <p>This is a top-level sibling of {@code config}/{@code runtime} (not nested
 * under {@code config}) so it can be surfaced in the lightweight
 * {@code /api/tree/status} view used to render the status page.
 *
 * <p>{@code icon} is optional (Lucide icon name, kebab-case, treated as an
 * opaque string by the backend). {@code colors} is an optional per-state
 * override; absent colours use the front-end default palette.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class Display {

    /** Lucide icon name (kebab-case), optional. */
    private String icon;

    /**
     * Name of a colour scheme (defined at the top of the config) to apply to this
     * node. If unset, the per-type default scheme is used. Individual entries in
     * {@link #colors} still override the scheme.
     */
    private String scheme;

    /** Per-state colour overrides. After resolution this holds the effective palette. */
    private DisplayColors colors;

    /** Optional override selecting the primary display metric (entities only). */
    private MetricSpec metric;

    public MetricSpec getMetric() {
        return metric;
    }

    public void setMetric(MetricSpec metric) {
        this.metric = metric;
    }

    public String getScheme() {
        return scheme;
    }

    public void setScheme(String scheme) {
        this.scheme = scheme;
    }

    public String getIcon() {
        return icon;
    }

    public void setIcon(String icon) {
        this.icon = icon;
    }

    public DisplayColors getColors() {
        return colors;
    }

    public void setColors(DisplayColors colors) {
        this.colors = colors;
    }
}
