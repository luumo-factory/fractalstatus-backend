package ai.luumo.fractalstatus.check.modules;

import ai.luumo.fractalstatus.check.CheckContext;
import ai.luumo.fractalstatus.check.CheckModule;
import ai.luumo.fractalstatus.check.CheckOutcome;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Passive check module that monitors MQTT topics rather than making outbound
 * probes. For each configured check instance the module subscribes (via
 * {@link MqttBrokerPool}) to a topic on the configured broker; the entity is
 * considered online as long as messages continue to arrive within the timeout
 * window.
 *
 * <p>Broker connections are multiplexed: all check instances that share the
 * same broker URL and credentials share a single {@link MqttBrokerPool} connection.
 * N entities on the same broker produce exactly one TCP connection.
 *
 * <p>Config keys:
 * <ul>
 *   <li>{@code broker} (required) - MQTT broker URI, e.g.
 *       {@code tcp://192.168.88.1:1883} or {@code ssl://broker:8883}.</li>
 *   <li>{@code topic} (required) - topic filter to subscribe to. Wildcards
 *       {@code +} (single level) and {@code #} (multi-level) are supported.</li>
 *   <li>{@code timeout} (seconds, default 60) - maximum age of the last message
 *       before the check is considered stale.</li>
 *   <li>{@code qos} (0/1/2, default 0)</li>
 *   <li>{@code username} (optional)</li>
 *   <li>{@code password} (optional)</li>
 *   <li>{@code interval} - scheduler re-evaluation period; should be well below
 *       {@code timeout} so staleness is detected promptly.</li>
 * </ul>
 *
 * <p>Output fields (always present):
 * <ul>
 *   <li>{@code lastSeenSeconds} (long; -1 when no message received yet)</li>
 *   <li>{@code timedOut} (boolean)</li>
 * </ul>
 *
 * <p>State rule example:
 * <pre>
 *   "offlineWhen": "heartbeat.timedOut == true or heartbeat.lastSeenSeconds &lt; 0"
 * </pre>
 *
 * <p>Example config:
 * <pre>
 * {
 *   "name": "heartbeat",
 *   "module": "mqtt",
 *   "config": {
 *     "broker":   "tcp://192.168.88.1:1883",
 *     "topic":    "home/sensor/temperature",
 *     "timeout":  60,
 *     "interval": 10
 *   }
 * }
 * </pre>
 */
@Component
public class MqttCheckModule implements CheckModule {

    static final int DEFAULT_TIMEOUT_SECONDS = 60;

    private final MqttStore store;
    private final MqttBrokerPool pool;

    public MqttCheckModule(MqttStore store, MqttBrokerPool pool) {
        this.store = store;
        this.pool = pool;
    }

    @Override
    public String id() {
        return "mqtt";
    }

    @Override
    public CheckOutcome check(CheckContext ctx) {
        String broker = ctx.getString("broker", "").trim();
        String topic  = ctx.getString("topic",  "").trim();
        int timeout   = Math.max(1, ctx.getInt("timeout", DEFAULT_TIMEOUT_SECONDS));
        int qos       = ctx.getInt("qos", 0);
        String username = ctx.getString("username", "").trim();
        String password = ctx.getString("password", "").trim();

        if (broker.isEmpty() || topic.isEmpty()) {
            return CheckOutcome.builder(false)
                    .put("lastSeenSeconds", -1L)
                    .put("timedOut", false)
                    .message("mqtt: 'broker' and 'topic' are required")
                    .build();
        }

        // Idempotent - only actually subscribes on the first call per check instance.
        pool.ensureSubscribed(
                broker, topic, qos,
                username.isEmpty() ? null : username,
                password.isEmpty() ? null : password,
                ctx.nodePath(), ctx.checkName(),
                store);

        // Evaluate staleness from the store.
        MqttStore.Entry entry = store.get(ctx.nodePath(), ctx.checkName()).orElse(null);

        if (entry == null) {
            return CheckOutcome.builder(false)
                    .put("lastSeenSeconds", -1L)
                    .put("timedOut", false)
                    .message("mqtt: no message received yet on '" + topic + "'")
                    .build();
        }

        long secondsAgo = Duration.between(entry.lastSeen(), Instant.now()).toSeconds();
        boolean stale = secondsAgo > timeout;

        if (stale) {
            return CheckOutcome.builder(false)
                    .put("lastSeenSeconds", secondsAgo)
                    .put("timedOut", true)
                    .message("mqtt: stale - last message " + secondsAgo + "s ago"
                            + " (timeout " + timeout + "s) on '" + topic + "'")
                    .build();
        }

        return CheckOutcome.builder(true)
                .put("lastSeenSeconds", secondsAgo)
                .put("timedOut", false)
                .message("mqtt: message received " + secondsAgo + "s ago on '" + topic + "'")
                .build();
    }
}
