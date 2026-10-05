package ai.luumo.fractalstatus.state;

import ai.luumo.fractalstatus.model.EntityNode;
import ai.luumo.fractalstatus.model.GroupNode;
import ai.luumo.fractalstatus.model.Node;
import ai.luumo.fractalstatus.model.State;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Derives a group's {@link State} as the worst state among its direct children.
 *
 * <p>Groups have no rules of their own: state is always computed automatically.
 * Because state is recomputed bottom-up (see {@code TreeStateUpdater}), a child
 * group already reflects the worst state of its own subtree, so a failure any
 * number of levels deep propagates up to every ancestor, including the root.
 */
@Component
public class RollupEvaluator {

    public State evaluate(GroupNode group) {
        List<Node> children = group.getChildren();
        if (children == null || children.isEmpty()) {
            return State.UNKNOWN;
        }
        State worst = State.UNKNOWN;
        for (Node child : children) {
            if (child.isHidden() || child.isDisabled()) {
                continue; // hidden/disabled nodes do not affect group state
            }
            worst = State.worst(worst, childState(child));
        }
        return worst;
    }

    private State childState(Node child) {
        if (child instanceof GroupNode g) {
            return g.getRuntime().getState();
        }
        if (child instanceof EntityNode e) {
            return e.getRuntime().getState();
        }
        return State.UNKNOWN;
    }
}
