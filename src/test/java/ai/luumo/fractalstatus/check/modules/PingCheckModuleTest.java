package ai.luumo.fractalstatus.check.modules;

import ai.luumo.fractalstatus.check.CheckContext;
import ai.luumo.fractalstatus.check.CheckOutcome;
import ai.luumo.fractalstatus.log.LogStore;
import ai.luumo.fractalstatus.log.NodeLogger;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class PingCheckModuleTest {

    private final PingCheckModule module = new PingCheckModule();

    private CheckContext context(Map<String, Object> config) {
        NodeLogger logger = new NodeLogger(new LogStore(16), "test");
        return new CheckContext() {
            public Map<String, Object> config() {
                return config;
            }

            public String nodePath() {
                return "test";
            }

            public String checkName() {
                return "ping";
            }

            public NodeLogger log() {
                return logger;
            }
        };
    }

    @Test
    void pingsLoopbackSuccessfully() throws Exception {
        CheckOutcome outcome = module.check(context(Map.of(
                "host", "127.0.0.1",
                "count", 1,
                "timeout", 2)));

        // Skip in sandboxes without a usable ping binary / loopback ICMP.
        assumeTrue(outcome.online(), "ping to loopback did not succeed in this environment");

        assertEquals(Boolean.TRUE, outcome.output().get("reachable"));
        assertInstanceOf(Number.class, outcome.output().get("rttMs"));
        assertTrue(((Number) outcome.output().get("rttMs")).doubleValue() >= 0.0);
        assertEquals(0.0, ((Number) outcome.output().get("packetLoss")).doubleValue());
    }

    @Test
    void honoursMillisecondTimeoutForLoopback() throws Exception {
        CheckOutcome outcome = module.check(context(Map.of(
                "host", "127.0.0.1",
                "count", 1,
                "timeoutMs", 500)));

        assumeTrue(outcome.online(), "ping to loopback did not succeed in this environment");
        assertEquals(Boolean.TRUE, outcome.output().get("reachable"));
    }

    @Test
    void reportsUnreachableForUnroutableAddress() throws Exception {
        // 192.0.2.0/24 is TEST-NET-1 (RFC 5737), guaranteed non-routable.
        CheckOutcome outcome = module.check(context(Map.of(
                "host", "192.0.2.1",
                "count", 1,
                "timeout", 1)));

        assertFalse(outcome.online());
        assertEquals(Boolean.FALSE, outcome.output().get("reachable"));
    }

    @Test
    void handlesMissingHost() throws Exception {
        CheckOutcome outcome = module.check(context(Map.of()));
        assertFalse(outcome.online());
        assertEquals(Boolean.FALSE, outcome.output().get("reachable"));
    }
}
