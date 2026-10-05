package ai.luumo.fractalstatus.state;

import ai.luumo.fractalstatus.model.EntityNode;
import ai.luumo.fractalstatus.model.GroupNode;
import ai.luumo.fractalstatus.model.NodeType;
import ai.luumo.fractalstatus.model.State;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RollupEvaluatorTest {

    private final RollupEvaluator evaluator = new RollupEvaluator();

    private EntityNode entity(State state) {
        EntityNode e = new EntityNode();
        e.setType(NodeType.ENTITY);
        e.getRuntime().setState(state);
        return e;
    }

    private GroupNode group(ai.luumo.fractalstatus.model.Node... children) {
        GroupNode g = new GroupNode();
        g.setType(NodeType.GROUP);
        for (var c : children) {
            g.getChildren().add(c);
        }
        return g;
    }

    @Test
    void emptyGroupIsUnknown() {
        assertEquals(State.UNKNOWN, evaluator.evaluate(group()));
    }

    @Test
    void worstOfChildrenWins() {
        assertEquals(State.OFFLINE,
                evaluator.evaluate(group(entity(State.ONLINE), entity(State.OFFLINE))));
        assertEquals(State.WARNING,
                evaluator.evaluate(group(entity(State.ONLINE), entity(State.WARNING))));
        assertEquals(State.ONLINE,
                evaluator.evaluate(group(entity(State.ONLINE), entity(State.ONLINE))));
    }

    @Test
    void failureThreeLevelsDeepPropagatesToRoot() {
        EntityNode deep = entity(State.OFFLINE);
        GroupNode level3 = group(deep);
        GroupNode level2 = group(level3, entity(State.ONLINE));
        GroupNode root = group(level2, entity(State.ONLINE));

        // Recompute bottom-up, as TreeStateUpdater does.
        level3.getRuntime().setState(evaluator.evaluate(level3));
        level2.getRuntime().setState(evaluator.evaluate(level2));
        root.getRuntime().setState(evaluator.evaluate(root));

        assertEquals(State.OFFLINE, level3.getRuntime().getState());
        assertEquals(State.OFFLINE, level2.getRuntime().getState());
        assertEquals(State.OFFLINE, root.getRuntime().getState());
    }
}
