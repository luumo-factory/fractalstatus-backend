package ai.luumo.fractalstatus.model.runtime;

/**
 * A primary display metric for an entity tile: a pre-formatted value plus a unit.
 *
 * @param value display-ready value (already rounded/formatted), e.g. "0.21", "200"
 * @param unit  unit label, possibly empty, e.g. "ms", "HTTP"
 */
public record Metric(String value, String unit) {
}
