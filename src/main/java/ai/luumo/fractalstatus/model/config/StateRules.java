package ai.luumo.fractalstatus.model.config;

import ai.luumo.fractalstatus.model.State;
import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Maps check output to an entity {@link State} using SpEL expressions.
 *
 * <p>The expressions are evaluated against a context whose root exposes each
 * check by name mapped to its structured {@code output} (e.g.
 * {@code ping.reachable}, {@code http.statusCode}). Evaluation order:
 * {@code offlineWhen} first, then {@code warningWhen}; if neither matches the
 * {@link #getFallback() fallback} applies.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class StateRules {

    /** SpEL; if true the entity is OFFLINE. */
    private String offlineWhen;

    /** SpEL; if true (and not offline) the entity is WARNING. */
    private String warningWhen;

    /** State when neither expression matches. Defaults to ONLINE. */
    private State fallback = State.ONLINE;

    public String getOfflineWhen() {
        return offlineWhen;
    }

    public void setOfflineWhen(String offlineWhen) {
        this.offlineWhen = offlineWhen;
    }

    public String getWarningWhen() {
        return warningWhen;
    }

    public void setWarningWhen(String warningWhen) {
        this.warningWhen = warningWhen;
    }

    public State getFallback() {
        return fallback;
    }

    public void setFallback(State fallback) {
        this.fallback = fallback;
    }
}
