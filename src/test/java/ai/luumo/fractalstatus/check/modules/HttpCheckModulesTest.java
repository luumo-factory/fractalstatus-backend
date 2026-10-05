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

class HttpCheckModulesTest {

    private final HttpCheckModule http = new HttpCheckModule();
    private final HttpBodyCheckModule httpBody = new HttpBodyCheckModule();

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
                return "http";
            }

            public NodeLogger log() {
                return logger;
            }
        };
    }

    @Test
    void httpReturns200WithValidSsl() throws Exception {
        CheckOutcome o = http.check(context(Map.of(
                "url", "https://example.com", "timeout", 8)));

        assumeTrue(((Number) o.output().get("statusCode")).intValue() != 0,
                "no outbound HTTPS in this environment");
        assertEquals(200, ((Number) o.output().get("statusCode")).intValue());
        assertEquals(Boolean.TRUE, o.output().get("sslValid"));
        assertInstanceOf(Number.class, o.output().get("responseTimeMs"));
        assertTrue(o.online());
    }

    @Test
    void httpBodyMatchesExpectedContent() throws Exception {
        CheckOutcome o = httpBody.check(context(Map.of(
                "url", "https://example.com",
                "expectBody", "Example Domain",
                "matchMode", "contains",
                "timeout", 8)));

        assumeTrue(((Number) o.output().get("statusCode")).intValue() == 200,
                "example.com not reachable in this environment");
        assertEquals(Boolean.TRUE, o.output().get("bodyMatched"));
        assertTrue(o.online());
    }

    @Test
    void httpBodyReportsMismatch() throws Exception {
        CheckOutcome o = httpBody.check(context(Map.of(
                "url", "https://example.com",
                "expectBody", "this-string-should-not-appear-zzz-123",
                "timeout", 8)));

        assumeTrue(((Number) o.output().get("statusCode")).intValue() == 200,
                "example.com not reachable in this environment");
        assertEquals(Boolean.FALSE, o.output().get("bodyMatched"));
        assertFalse(o.online());
    }

    @Test
    void handlesUnreachableHostGracefully() throws Exception {
        // RFC 5737 TEST-NET-1: non-routable, forces connect timeout (not a hang).
        CheckOutcome o = http.check(context(Map.of(
                "url", "https://192.0.2.1", "timeout", 2)));

        assertFalse(o.online());
        assertEquals(0, ((Number) o.output().get("statusCode")).intValue());
    }

    @Test
    void handlesMissingUrl() throws Exception {
        CheckOutcome o = http.check(context(Map.of()));
        assertFalse(o.online());
        assertEquals(0, ((Number) o.output().get("statusCode")).intValue());
    }
}
