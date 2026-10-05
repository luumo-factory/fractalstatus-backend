package ai.luumo.fractalstatus.check.modules;

import ai.luumo.fractalstatus.check.CheckContext;
import ai.luumo.fractalstatus.check.CheckOutcome;
import ai.luumo.fractalstatus.log.LogStore;
import ai.luumo.fractalstatus.log.NodeLogger;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

class DnsCheckModuleTest {

    private final DnsCheckModule module = new DnsCheckModule();

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
                return "dns";
            }

            public NodeLogger log() {
                return logger;
            }
        };
    }

    @Test
    void resolvesNsAcrossBothResolvers() throws Exception {
        CheckOutcome o = module.check(context(Map.of(
                "domain", "luumo.ai",
                "type", "NS",
                "servers", List.of("8.8.8.8", "1.1.1.1"),
                "timeout", 5)));

        assumeTrue(((Number) o.output().get("serversOk")).intValue() > 0,
                "no outbound DNS in this environment");
        assertTrue(o.online());
        assertEquals(Boolean.TRUE, o.output().get("resolved"));
        assertEquals(2, ((Number) o.output().get("serversTotal")).intValue());
        assertEquals(2, ((Number) o.output().get("serversOk")).intValue());
        assertTrue(((Number) o.output().get("recordCount")).intValue() > 0);
    }

    @Test
    void failsWhenRecordTypeAbsent() throws Exception {
        // creswick.eu has no A record -> should be treated as a failure.
        CheckOutcome o = module.check(context(Map.of(
                "domain", "creswick.eu",
                "type", "A",
                "servers", List.of("8.8.8.8", "1.1.1.1"),
                "timeout", 5)));

        assumeTrue(((Number) o.output().get("serversTotal")).intValue() == 2, "n/a");
        assertFalse(o.online());
        assertEquals(Boolean.FALSE, o.output().get("resolved"));
    }

    @Test
    void handlesMissingDomain() throws Exception {
        CheckOutcome o = module.check(context(Map.of()));
        assertFalse(o.online());
        assertEquals(Boolean.FALSE, o.output().get("resolved"));
    }
}
