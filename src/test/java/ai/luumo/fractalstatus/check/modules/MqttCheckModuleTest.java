package ai.luumo.fractalstatus.check.modules;

import ai.luumo.fractalstatus.check.CheckContext;
import ai.luumo.fractalstatus.check.CheckOutcome;
import ai.luumo.fractalstatus.log.LogStore;
import ai.luumo.fractalstatus.log.NodeLogger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for {@link MqttCheckModule}.
 *
 * <p>These tests inject directly into {@link MqttStore} (bypassing the broker)
 * so no MQTT broker is required. The {@link MqttBrokerPool} is replaced with a
 * no-op stub - we are testing the staleness-evaluation logic only.
 */
class MqttCheckModuleTest {

    private MqttStore store;
    private MqttCheckModule module;

    private static final String ENTITY = "root.test.sensor";
    private static final String CHECK  = "heartbeat";
    private static final String BROKER = "tcp://localhost:1883";
    private static final String TOPIC  = "home/sensor/heartbeat";

    @BeforeEach
    void setUp() {
        store = new MqttStore();
        // Stub pool - ensureSubscribed is a no-op in unit tests.
        MqttBrokerPool stubPool = new MqttBrokerPool() {
            @Override
            public void ensureSubscribed(String brokerUrl, String topic, int qos,
                                          String username, String password,
                                          String entityPath, String checkName,
                                          MqttStore store) {
                // No-op: tests inject messages directly into the store.
            }
        };
        module = new MqttCheckModule(store, stubPool);
    }

    private CheckContext ctx(Map<String, Object> config) {
        NodeLogger logger = new NodeLogger(new LogStore(16), ENTITY);
        return new CheckContext() {
            public Map<String, Object> config() { return config; }
            public String nodePath()            { return ENTITY; }
            public String checkName()           { return CHECK; }
            public NodeLogger log()             { return logger; }
        };
    }

    private Map<String, Object> baseConfig() {
        return Map.of("broker", BROKER, "topic", TOPIC, "timeout", 60);
    }

    // --- configuration validation ---

    @Test
    void missingBrokerFails() {
        CheckOutcome o = module.check(ctx(Map.of("topic", TOPIC)));
        assertFalse(o.online());
        assertEquals(-1L, o.output().get("lastSeenSeconds"));
        assertTrue(o.message().contains("required"));
    }

    @Test
    void missingTopicFails() {
        CheckOutcome o = module.check(ctx(Map.of("broker", BROKER)));
        assertFalse(o.online());
        assertEquals(-1L, o.output().get("lastSeenSeconds"));
        assertTrue(o.message().contains("required"));
    }

    // --- no message yet ---

    @Test
    void noMessageReceivedIsOffline() {
        CheckOutcome o = module.check(ctx(baseConfig()));
        assertFalse(o.online());
        assertEquals(-1L, o.output().get("lastSeenSeconds"));
        assertEquals(Boolean.FALSE, o.output().get("timedOut"));
        assertTrue(o.message().contains("no message"));
    }

    // --- fresh message ---

    @Test
    void freshMessageIsOnline() {
        store.recordMessage(ENTITY, CHECK, "ping");

        CheckOutcome o = module.check(ctx(baseConfig()));

        assertTrue(o.online());
        assertEquals(Boolean.FALSE, o.output().get("timedOut"));
        long lastSeen = ((Number) o.output().get("lastSeenSeconds")).longValue();
        assertTrue(lastSeen >= 0, "lastSeenSeconds should be non-negative");
    }

    @Test
    void emptyPayloadStillCountsAsMessage() {
        store.recordMessage(ENTITY, CHECK, "");

        CheckOutcome o = module.check(ctx(baseConfig()));

        assertTrue(o.online());
    }

    // --- staleness ---

    @Test
    void staleMessageIsOffline() {
        backdateMessage(120); // 120s old, timeout 60

        CheckOutcome o = module.check(ctx(baseConfig()));

        assertFalse(o.online());
        assertEquals(Boolean.TRUE, o.output().get("timedOut"));
        assertTrue(o.message().contains("stale"));
    }

    @Test
    void messageJustWithinTimeoutIsOnline() {
        backdateMessage(59);

        CheckOutcome o = module.check(ctx(baseConfig()));

        assertTrue(o.online());
        assertEquals(Boolean.FALSE, o.output().get("timedOut"));
    }

    @Test
    void messageJustBeyondTimeoutIsOffline() {
        backdateMessage(61);

        CheckOutcome o = module.check(ctx(baseConfig()));

        assertFalse(o.online());
        assertEquals(Boolean.TRUE, o.output().get("timedOut"));
    }

    @Test
    void defaultTimeoutOf60sIsApplied() {
        // No timeout in config - default is 60s. Backdate 90s.
        backdateMessage(90);

        CheckOutcome o = module.check(ctx(Map.of("broker", BROKER, "topic", TOPIC)));

        assertFalse(o.online());
        assertEquals(Boolean.TRUE, o.output().get("timedOut"));
    }

    @Test
    void customTimeoutIsRespected() {
        backdateMessage(45); // 45s old, timeout 30

        CheckOutcome o = module.check(ctx(
                Map.of("broker", BROKER, "topic", TOPIC, "timeout", 30)));

        assertFalse(o.online());
        assertEquals(Boolean.TRUE, o.output().get("timedOut"));
    }

    // --- last seen seconds ---

    @Test
    void lastSeenSecondsIsAccurate() {
        backdateMessage(10);

        CheckOutcome o = module.check(ctx(baseConfig()));

        assertTrue(o.online());
        long lastSeen = ((Number) o.output().get("lastSeenSeconds")).longValue();
        // Allow 1s of test execution slack
        assertTrue(lastSeen >= 10 && lastSeen <= 12,
                "expected ~10s, got " + lastSeen);
    }

    // --- helpers ---

    private void backdateMessage(long secondsAgo) {
        store.putEntryForTest(ENTITY, CHECK,
                new MqttStore.Entry(Instant.now().minusSeconds(secondsAgo), "ping"));
    }
}
