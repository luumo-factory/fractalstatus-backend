package ai.luumo.fractalstatus.check.modules;

import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory store for MQTT message timestamps and payloads, shared between
 * {@link MqttBrokerPool} (writes on message arrival) and
 * {@link MqttCheckModule} (reads on each scheduler tick).
 *
 * <p>Keyed by a composite of entity path and check name, mirroring the pattern
 * used by {@link WebhookStore}.
 */
@Component
public class MqttStore {

    /**
     * A single recorded message.
     *
     * @param lastSeen when the message arrived
     * @param payload  raw UTF-8 payload string (may be empty but never null)
     */
    public record Entry(Instant lastSeen, String payload) {}

    private final ConcurrentHashMap<String, Entry> entries = new ConcurrentHashMap<>();

    /** Called by {@link MqttBrokerPool} on each incoming message. */
    public void recordMessage(String entityPath, String checkName, String payload) {
        entries.put(key(entityPath, checkName),
                new Entry(Instant.now(), payload == null ? "" : payload));
    }

    /** Returns the latest entry, or {@link Optional#empty()} if no message received yet. */
    public Optional<Entry> get(String entityPath, String checkName) {
        return Optional.ofNullable(entries.get(key(entityPath, checkName)));
    }

    /** Package-private test helper to insert backdated entries. */
    void putEntryForTest(String entityPath, String checkName, Entry entry) {
        entries.put(key(entityPath, checkName), entry);
    }

    public void clear() {
        entries.clear();
    }

    private static String key(String entityPath, String checkName) {
        return entityPath + '\0' + checkName;
    }
}
