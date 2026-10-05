package ai.luumo.fractalstatus.state;

import ai.luumo.fractalstatus.model.EntityNode;
import ai.luumo.fractalstatus.model.State;
import ai.luumo.fractalstatus.model.config.StateRules;
import ai.luumo.fractalstatus.model.runtime.CheckResult;
import org.springframework.context.expression.MapAccessor;
import org.springframework.expression.Expression;
import org.springframework.expression.ExpressionParser;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.SimpleEvaluationContext;
import org.springframework.stereotype.Component;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Derives an entity's {@link State} from its check results by evaluating the
 * SpEL {@code offlineWhen} / {@code warningWhen} rules.
 *
 * <p>The evaluation context root is a map of {@code checkName -> output map}, so
 * expressions read like {@code ping.reachable == false} or
 * {@code http.statusCode != 200}. A {@link SimpleEvaluationContext} restricted to
 * a {@link MapAccessor} is used so expressions can only read map properties (no
 * method calls or type references), keeping evaluation of config-authored
 * expressions safe.
 */
@Component
public class StateEvaluator {

    private final ExpressionParser parser = new SpelExpressionParser();
    private final Map<String, Expression> cache = new ConcurrentHashMap<>();

    /**
     * Computes the entity state from its current runtime check results. Returns
     * {@link State#UNKNOWN} when no check has produced a result yet.
     */
    public State evaluate(EntityNode entity) {
        Map<String, CheckResult> results = entity.getRuntime().results();
        if (results.isEmpty()) {
            return State.UNKNOWN;
        }

        Map<String, Object> root = new HashMap<>();
        for (CheckResult r : results.values()) {
            root.put(r.getName(), r.getOutput());
        }

        StateRules rules = entity.getConfig().getState();
        if (rules == null) {
            return State.UNKNOWN;
        }

        if (evaluateBoolean(rules.getOfflineWhen(), root, entity.getPath())) {
            return State.OFFLINE;
        }
        if (evaluateBoolean(rules.getWarningWhen(), root, entity.getPath())) {
            return State.WARNING;
        }
        return rules.getFallback() == null ? State.ONLINE : rules.getFallback();
    }

    private boolean evaluateBoolean(String expression, Map<String, Object> root, String path) {
        if (expression == null || expression.isBlank()) {
            return false;
        }
        try {
            Expression expr = cache.computeIfAbsent(expression, parser::parseExpression);
            SimpleEvaluationContext context = SimpleEvaluationContext
                    .forPropertyAccessors(new MapAccessor())
                    .withRootObject(root)
                    .build();
            Boolean result = expr.getValue(context, Boolean.class);
            return Boolean.TRUE.equals(result);
        } catch (RuntimeException ex) {
            // Missing field, type mismatch, etc. Treat as "not matched".
            return false;
        }
    }
}
