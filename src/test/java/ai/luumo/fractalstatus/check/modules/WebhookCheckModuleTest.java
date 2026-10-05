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
 * Unit tests for {@link WebhookCheckModule}.
 *
 * No Spring context needed - the store is constructed directly.
 */
class WebhookCheckModuleTest {

    private WebhookStore store;
    private WebhookCheckModule module;

    private static final String ENTITY = "root.test.entity";
    private static final String CHECK  = "heartbeat";

    @BeforeEach
    void setUp() {
        store  = new WebhookStore();
        module = new WebhookCheckModule(store);
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

    // --- never pinged ---

    @Test
    void neverPingedIsOffline() {
        CheckOutcome o = module.check(ctx(Map.of("timeout", 60)));

        assertFalse(o.online());
        assertEquals(-1L, o.output().get("lastSeenSeconds"));
        assertEquals(Boolean.FALSE, o.output().get("timedOut"));
        assertTrue(o.message().contains("no webhook"));
    }

    // --- ping mode ---

    @Test
    void freshPingIsOnline() {
        store.recordPing(ENTITY, CHECK);

        CheckOutcome o = module.check(ctx(Map.of("timeout", 60)));

        assertTrue(o.online());
        assertEquals(Boolean.FALSE, o.output().get("timedOut"));
        Number lastSeen = (Number) o.output().get("lastSeenSeconds");
        assertTrue(lastSeen.longValue() >= 0, "lastSeenSeconds should be non-negative");
    }

    @Test
    void stalePingIsOffline() {
        // Backdate the entry so it appears old.
        backdatePing(120); // 120 seconds old, timeout is 60

        CheckOutcome o = module.check(ctx(Map.of("timeout", 60)));

        assertFalse(o.online());
        assertEquals(Boolean.TRUE, o.output().get("timedOut"));
        assertTrue(o.message().contains("stale"));
    }

    @Test
    void pingJustWithinTimeoutIsOnline() {
        backdatePing(59); // 59 seconds old, timeout is 60

        CheckOutcome o = module.check(ctx(Map.of("timeout", 60)));

        assertTrue(o.online());
        assertEquals(Boolean.FALSE, o.output().get("timedOut"));
    }

    @Test
    void pingExactlyAtTimeoutIsOffline() {
        backdatePing(61); // 61 seconds old, timeout is 60

        CheckOutcome o = module.check(ctx(Map.of("timeout", 60)));

        assertFalse(o.online());
        assertEquals(Boolean.TRUE, o.output().get("timedOut"));
    }

    @Test
    void defaultTimeoutIsApplied() {
        // No timeout in config -> uses default (60s). Backdate to 90s - should be stale.
        backdatePing(90);

        CheckOutcome o = module.check(ctx(Map.of())); // no timeout key

        assertFalse(o.online());
        assertEquals(Boolean.TRUE, o.output().get("timedOut"));
    }

    // --- json mode ---

    @Test
    void jsonModeWithPayloadAndImpliedOnlineIsOnline() {
        store.recordPayload(ENTITY, CHECK, Map.of("version", "1.2.3"));

        CheckOutcome o = module.check(ctx(Map.of("mode", "json", "timeout", 60)));

        assertTrue(o.online());
        assertEquals(Boolean.FALSE, o.output().get("timedOut"));
        assertEquals("1.2.3", o.output().get("version"));
    }

    @Test
    void jsonModePayloadExplicitlyOnlineFalseIsOffline() {
        store.recordPayload(ENTITY, CHECK, Map.of("online", false, "message", "disk full"));

        CheckOutcome o = module.check(ctx(Map.of("mode", "json", "timeout", 60)));

        assertFalse(o.online());
        assertEquals("disk full", o.message());
        // online and message fields are NOT passed into output (they drive the outcome)
        assertFalse(o.output().containsKey("online"));
        assertFalse(o.output().containsKey("message"));
    }

    @Test
    void jsonModePayloadStringOnlineFalseIsOffline() {
        store.recordPayload(ENTITY, CHECK, Map.of("online", "false"));

        CheckOutcome o = module.check(ctx(Map.of("mode", "json", "timeout", 60)));

        assertFalse(o.online());
    }

    @Test
    void jsonModeExtraPayloadFieldsInOutput() {
        store.recordPayload(ENTITY, CHECK,
                Map.of("diskFreeGb", 12.5, "cpuPercent", 42));

        CheckOutcome o = module.check(ctx(Map.of("mode", "json", "timeout", 60)));

        assertTrue(o.online());
        assertEquals(12.5, ((Number) o.output().get("diskFreeGb")).doubleValue(), 0.001);
        assertEquals(42, ((Number) o.output().get("cpuPercent")).intValue());
    }

    @Test
    void jsonModeNoPingYetIsOffline() {
        // Nothing recorded - neverPinged path applies in json mode too.
        CheckOutcome o = module.check(ctx(Map.of("mode", "json", "timeout", 60)));

        assertFalse(o.online());
        assertEquals(-1L, o.output().get("lastSeenSeconds"));
    }

    @Test
    void jsonModeWithNullPayloadFailsGracefully() {
        // A GET or bodyless POST was recorded as a ping (null payload).
        store.recordPing(ENTITY, CHECK); // null payload

        CheckOutcome o = module.check(ctx(Map.of("mode", "json", "timeout", 60)));

        assertFalse(o.online());
        assertTrue(o.message().contains("no JSON payload"));
    }

    @Test
    void jsonModeStalePayloadIsOffline() {
        store.recordPayload(ENTITY, CHECK, Map.of("version", "1.0"));
        backdatePayload(120); // 120s old, timeout 60

        CheckOutcome o = module.check(ctx(Map.of("mode", "json", "timeout", 60)));

        assertFalse(o.online());
        assertEquals(Boolean.TRUE, o.output().get("timedOut"));
    }

    @Test
    void jsonModeSynthesisesDefaultMessage() {
        store.recordPayload(ENTITY, CHECK, Map.of("cpuPercent", 5));

        CheckOutcome o = module.check(ctx(Map.of("mode", "json", "timeout", 60)));

        assertTrue(o.online());
        assertEquals("webhook received", o.message());
    }

    @Test
    void jsonModeUsesCustomMessage() {
        store.recordPayload(ENTITY, CHECK, Map.of("message", "all good", "cpuPercent", 3));

        CheckOutcome o = module.check(ctx(Map.of("mode", "json", "timeout", 60)));

        assertTrue(o.online());
        assertEquals("all good", o.message());
        // message is NOT doubled up in the output map
        assertFalse(o.output().containsKey("message"));
    }

    // --- helpers ---

    /**
     * Inserts a ping entry backdated by {@code secondsAgo}.
     * Uses the package-private test helper on {@link WebhookStore}.
     */
    private void backdatePing(long secondsAgo) {
        store.putEntryForTest(ENTITY, CHECK,
                new WebhookStore.Entry(Instant.now().minusSeconds(secondsAgo), null));
    }

    private void backdatePayload(long secondsAgo) {
        store.putEntryForTest(ENTITY, CHECK,
                new WebhookStore.Entry(Instant.now().minusSeconds(secondsAgo),
                        Map.of("version", "1.0")));
    }
}
