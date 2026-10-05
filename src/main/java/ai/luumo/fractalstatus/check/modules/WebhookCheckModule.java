package ai.luumo.fractalstatus.check.modules;

import ai.luumo.fractalstatus.check.CheckContext;
import ai.luumo.fractalstatus.check.CheckModule;
import ai.luumo.fractalstatus.check.CheckOutcome;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;

/**
 * Passive check module: instead of making outbound probes, it waits for
 * inbound HTTP calls to {@code /webhooks/<entity-path>/<check-name>} (handled
 * by {@link ai.luumo.fractalstatus.api.WebhookController}).
 *
 * <p>On each scheduler tick, {@link #check(CheckContext)} reads from the
 * {@link WebhookStore} and derives the current health signal:
 * <ul>
 *   <li>Never pinged - offline.</li>
 *   <li>Last ping older than {@code timeout} seconds - offline (stale).</li>
 *   <li>Last ping within {@code timeout} seconds:
 *     <ul>
 *       <li>{@code mode: ping} (default) - any GET or POST = online.</li>
 *       <li>{@code mode: json} - POST body must be valid JSON; an optional
 *           {@code online} boolean field drives the health signal; all other
 *           fields are passed through as check output for use in SpEL state
 *           rules.</li>
 *     </ul>
 *   </li>
 * </ul>
 *
 * <p>The {@link ai.luumo.fractalstatus.api.WebhookController} also triggers an
 * immediate state update when a ping arrives, so the UI reflects changes at
 * webhook-time rather than waiting for the next scheduler tick.
 *
 * <p>Config keys:
 * <ul>
 *   <li>{@code timeout} (seconds, default 60) - maximum age of the last ping
 *       before the check is considered stale.</li>
 *   <li>{@code mode} (default {@code ping}) - {@code ping} or {@code json}.</li>
 *   <li>{@code secret} (optional) - if set, inbound requests must supply
 *       this value in the {@code X-Webhook-Secret} header; mismatches are
 *       rejected with HTTP 403.</li>
 *   <li>{@code interval} - how often the scheduler re-evaluates staleness.
 *       Should be well below {@code timeout}; e.g. if timeout is 60, set
 *       interval to 10 so staleness is detected within 10 seconds.</li>
 * </ul>
 *
 * <p>Output fields (always present):
 * <ul>
 *   <li>{@code lastSeenSeconds} (long; -1 when never pinged)</li>
 *   <li>{@code timedOut} (boolean)</li>
 *   <li>Any additional fields from the JSON payload ({@code mode: json} only)</li>
 * </ul>
 *
 * <p>Webhook URL: {@code /webhooks/<group>/<group>/.../<entity>/<checkName>}
 * where the path segments (excluding the final check name) are the entity's
 * node IDs joined by {@code /}. The root node ID may be omitted.
 *
 * <p>Example - entity path {@code root.web.luumo.globalLuumo}, check name
 * {@code heartbeat}:
 * <pre>
 *   GET  /webhooks/web/luumo/globalLuumo/heartbeat
 *   POST /webhooks/web/luumo/globalLuumo/heartbeat
 *   POST /webhooks/web/luumo/globalLuumo/heartbeat   (with JSON body, mode: json)
 * </pre>
 */
@Component
public class WebhookCheckModule implements CheckModule {

    /** Default staleness window when no {@code timeout} is configured. */
    static final int DEFAULT_TIMEOUT_SECONDS = 60;

    private final WebhookStore store;

    public WebhookCheckModule(WebhookStore store) {
        this.store = store;
    }

    @Override
    public String id() {
        return "webhook";
    }

    @Override
    public CheckOutcome check(CheckContext ctx) {
        int timeout = Math.max(1, ctx.getInt("timeout", DEFAULT_TIMEOUT_SECONDS));
        String mode = ctx.getString("mode", "ping").toLowerCase(Locale.ROOT);

        WebhookStore.Entry entry = store.get(ctx.nodePath(), ctx.checkName()).orElse(null);

        // --- never pinged ---
        if (entry == null) {
            return CheckOutcome.builder(false)
                    .put("lastSeenSeconds", -1L)
                    .put("timedOut", false)
                    .message("no webhook received yet")
                    .build();
        }

        long secondsAgo = Duration.between(entry.lastSeen(), Instant.now()).toSeconds();
        boolean stale = secondsAgo > timeout;

        // --- stale ---
        if (stale) {
            return CheckOutcome.builder(false)
                    .put("lastSeenSeconds", secondsAgo)
                    .put("timedOut", true)
                    .message("webhook stale: last received " + secondsAgo + "s ago"
                            + " (timeout " + timeout + "s)")
                    .build();
        }

        // --- fresh, json mode ---
        if ("json".equals(mode)) {
            Map<String, Object> payload = entry.payload();
            if (payload == null) {
                return CheckOutcome.builder(false)
                        .put("lastSeenSeconds", secondsAgo)
                        .put("timedOut", false)
                        .message("webhook received but no JSON payload (mode is json)")
                        .build();
            }
            return buildJsonOutcome(payload, secondsAgo);
        }

        // --- fresh, ping mode ---
        return CheckOutcome.builder(true)
                .put("lastSeenSeconds", secondsAgo)
                .put("timedOut", false)
                .message("webhook received " + secondsAgo + "s ago")
                .build();
    }

    /**
     * Builds an outcome from a JSON payload. The payload may include:
     * <ul>
     *   <li>{@code online} (boolean or string) - health signal; default true.</li>
     *   <li>{@code message} (string) - human-readable status; auto-generated if
     *       absent.</li>
     *   <li>Any other fields are copied into the check output and addressable from
     *       SpEL state rules.</li>
     * </ul>
     */
    private static CheckOutcome buildJsonOutcome(Map<String, Object> payload, long secondsAgo) {
        Object onlineField = payload.get("online");
        boolean online = true;
        if (onlineField instanceof Boolean b) {
            online = b;
        } else if (onlineField instanceof String s) {
            online = Boolean.parseBoolean(s.trim());
        }

        String message = payload.containsKey("message")
                ? String.valueOf(payload.get("message"))
                : (online ? "webhook received" : "webhook reported offline");

        CheckOutcome.Builder builder = CheckOutcome.builder(online)
                .put("lastSeenSeconds", secondsAgo)
                .put("timedOut", false)
                .message(message);

        // Pass through all extra payload fields so they are addressable from
        // SpEL state rules (e.g. checkName.diskFreeGb > 5).
        for (Map.Entry<String, Object> e : payload.entrySet()) {
            String key = e.getKey();
            if (!"online".equals(key) && !"message".equals(key)) {
                builder.put(key, e.getValue());
            }
        }

        return builder.build();
    }
}
