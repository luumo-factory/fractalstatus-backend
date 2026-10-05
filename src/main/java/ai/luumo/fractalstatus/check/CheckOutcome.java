package ai.luumo.fractalstatus.check;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * The result of a single check execution.
 *
 * @param online  the module's primary health signal
 * @param output  structured, module-specific fields (camelCase keys so they are
 *                addressable from SpEL state rules, e.g. {@code ping.rttMs})
 * @param message optional human-readable message
 */
public record CheckOutcome(boolean online, Map<String, Object> output, String message) {

    public CheckOutcome {
        output = output == null ? Map.of() : output;
    }

    public static Builder builder(boolean online) {
        return new Builder(online);
    }

    public static final class Builder {
        private final boolean online;
        private final Map<String, Object> output = new LinkedHashMap<>();
        private String message;

        private Builder(boolean online) {
            this.online = online;
        }

        public Builder put(String key, Object value) {
            output.put(key, value);
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public CheckOutcome build() {
            return new CheckOutcome(online, output, message);
        }
    }
}
