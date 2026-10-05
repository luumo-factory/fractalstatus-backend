package ai.luumo.fractalstatus.model.config;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Optional per-entity override selecting the primary display metric
 * ({@code runtime.metric}). Useful for entities with multiple checks.
 *
 * <ul>
 *   <li>{@code check} - name of the check to read from (default: the entity's
 *       first check).</li>
 *   <li>{@code field} - output field to display. If set, that field's value is
 *       shown; if unset, the check module's default metric is used.</li>
 *   <li>{@code unit} - unit label; overrides the module default unit.</li>
 *   <li>{@code decimals} - decimal places when {@code field} is numeric.</li>
 * </ul>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class MetricSpec {

    private String check;
    private String field;
    private String unit;
    private Integer decimals;

    public String getCheck() {
        return check;
    }

    public void setCheck(String check) {
        this.check = check;
    }

    public String getField() {
        return field;
    }

    public void setField(String field) {
        this.field = field;
    }

    public String getUnit() {
        return unit;
    }

    public void setUnit(String unit) {
        this.unit = unit;
    }

    public Integer getDecimals() {
        return decimals;
    }

    public void setDecimals(Integer decimals) {
        this.decimals = decimals;
    }
}
