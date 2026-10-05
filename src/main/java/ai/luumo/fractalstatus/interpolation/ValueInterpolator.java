package ai.luumo.fractalstatus.interpolation;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Resolves {@code ${this.<key>}} placeholders inside check configuration against
 * an entity's values.
 *
 * <p>This is deliberately plain string substitution (not SpEL): it runs once
 * when a live check instance is built, and the keys follow the identifier rule
 * ({@code [A-Za-z_][A-Za-z0-9_]*}).
 */
public final class ValueInterpolator {

    private static final Pattern PLACEHOLDER =
            Pattern.compile("\\$\\{this\\.([A-Za-z_][A-Za-z0-9_]*)}");

    private ValueInterpolator() {
    }

    /**
     * Returns a deep copy of {@code config} with placeholders replaced using
     * {@code values}. Nested maps and lists are walked recursively; scalar
     * values are left untouched.
     */
    @SuppressWarnings("unchecked")
    public static Map<String, Object> resolve(Map<String, Object> config, Map<String, Object> values) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<String, Object> e : config.entrySet()) {
            out.put(e.getKey(), resolveValue(e.getValue(), values));
        }
        return out;
    }

    @SuppressWarnings("unchecked")
    private static Object resolveValue(Object value, Map<String, Object> values) {
        if (value instanceof String s) {
            return resolveString(s, values);
        }
        if (value instanceof Map<?, ?> m) {
            return resolve((Map<String, Object>) m, values);
        }
        if (value instanceof List<?> list) {
            List<Object> out = new ArrayList<>(list.size());
            for (Object item : list) {
                out.add(resolveValue(item, values));
            }
            return out;
        }
        return value;
    }

    private static Object resolveString(String s, Map<String, Object> values) {
        Matcher m = PLACEHOLDER.matcher(s);
        // Whole-string placeholder: preserve the original value type.
        if (m.matches()) {
            return values.get(m.group(1));
        }
        StringBuilder sb = new StringBuilder();
        m.reset();
        while (m.find()) {
            Object v = values.get(m.group(1));
            m.appendReplacement(sb, Matcher.quoteReplacement(v == null ? "" : String.valueOf(v)));
        }
        m.appendTail(sb);
        return sb.toString();
    }
}
