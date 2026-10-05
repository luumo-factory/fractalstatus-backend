package ai.luumo.fractalstatus.state;

import ai.luumo.fractalstatus.log.LogLevel;
import ai.luumo.fractalstatus.log.LogStore;
import ai.luumo.fractalstatus.model.EntityNode;
import ai.luumo.fractalstatus.model.GroupNode;
import ai.luumo.fractalstatus.model.State;
import org.springframework.stereotype.Component;

/**
 * Recomputes entity state after a check updates, then rolls the change up
 * through ancestor groups. Serialized so concurrent check threads see a
 * consistent tree.
 */
@Component
public class TreeStateUpdater {

    private final StateEvaluator stateEvaluator;
    private final RollupEvaluator rollupEvaluator;
    private final MetricResolver metricResolver;
    private final LogStore log;

    public TreeStateUpdater(StateEvaluator stateEvaluator,
                            RollupEvaluator rollupEvaluator,
                            MetricResolver metricResolver,
                            LogStore log) {
        this.stateEvaluator = stateEvaluator;
        this.rollupEvaluator = rollupEvaluator;
        this.metricResolver = metricResolver;
        this.log = log;
    }

    public synchronized void onCheckUpdated(EntityNode entity) {
        State previous = entity.getRuntime().getState();
        State next = stateEvaluator.evaluate(entity);
        if (next != previous) {
            entity.getRuntime().setState(next);
            log.log(LogLevel.INFO, entity.getPath(),
                    "state " + previous.json() + " -> " + next.json());
        }
        entity.getRuntime().setMetric(metricResolver.resolve(entity));
        rollUp(entity.getParent());
    }

    private void rollUp(GroupNode group) {
        while (group != null) {
            State previous = group.getRuntime().getState();
            State next = rollupEvaluator.evaluate(group);
            if (next != previous) {
                group.getRuntime().setState(next);
                log.log(LogLevel.INFO, group.getPath(),
                        "state " + previous.json() + " -> " + next.json());
            }
            group = group.getParent();
        }
    }
}
