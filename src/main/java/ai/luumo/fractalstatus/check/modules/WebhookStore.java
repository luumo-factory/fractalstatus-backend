package ai.luumo.fractalstatus.check.modules;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for inbound webhook ping state, shared between
 * {@link WebhookCheckModule} (which reads it on each scheduler tick) and
 * {@link ai.luumo.fractalstatus.api.WebhookController} (which writes it on
 * each inbound request).
 *
 * <p>Keyed by a composite of entity path and check name so multiple webhook
 * checks can coexist on the same entity.
 */
@Component
public class WebhookStore {

    /**
     * A single recorded ping.
     *
     * @param lastSeen when the ping arrived
     * @param payload  the parsed JSON body for {@code mode: json} calls, or
     *                 {@code null} for plain pings and GET requests
     */
    public record Entry(Instant lastSeen, Map<String, Object> payload) {}

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    // --- writes ---

    /** Records a plain ping (no payload). */
    public void recordPing(String entityPath, String checkName) {
        entries.put(key(entityPath, checkName), new Entry(Instant.now(), null));
    }

    /** Records a ping that carries a JSON payload. */
    public void recordPayload(String entityPath, String checkName, Map<String, Object> payload) {
        entries.put(key(entityPath, checkName), new Entry(Instant.now(), payload));
    }

    // --- reads ---

    /** Returns the latest entry, or {@link Optional#empty()} if never pinged. */
    public Optional<Entry> get(String entityPath, String checkName) {
        return Optional.ofNullable(entries.get(key(entityPath, checkName)));
    }

    // --- test support (package-private) ---

    /** Inserts an entry directly, used only by unit tests to backdate timestamps. */
    void putEntryForTest(String entityPath, String checkName, Entry entry) {
        entries.put(key(entityPath, checkName), entry);
    }

    // --- housekeeping ---

    /** Removes a single entry (e.g. after a config reload). */
    public void remove(String entityPath, String checkName) {
        entries.remove(key(entityPath, checkName));
    }

    /** Clears all entries. */
    public void clear() {
        entries.clear();
    }

    private static String key(String entityPath, String checkName) {
        return entityPath + '\0' + checkName;
    }
}
