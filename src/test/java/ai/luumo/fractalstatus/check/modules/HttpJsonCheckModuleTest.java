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

/**
 * Tests for {@link HttpJsonCheckModule}.
 *
 * <p>Tests that require live network connectivity use {@code assumeTrue} to
 * skip gracefully in environments without outbound HTTPS.
 */
class HttpJsonCheckModuleTest {

    private final HttpJsonCheckModule module = new HttpJsonCheckModule();

    private CheckContext context(Map<String, Object> config) {
        NodeLogger logger = new NodeLogger(new LogStore(16), "test");
        return new CheckContext() {
            public Map<String, Object> config() { return config; }
            public String nodePath()  { return "test"; }
            public String checkName() { return "http-json"; }
            public NodeLogger log()   { return logger; }
        };
    }

    // --- error / edge case tests (no network required) ---

    @Test
    void missingUrlFails() {
        CheckOutcome o = module.check(context(Map.of()));
        assertFalse(o.online());
        assertEquals(0, ((Number) o.output().get("statusCode")).intValue());
        assertEquals(Boolean.FALSE, o.output().get("jsonValid"));
        assertEquals(Boolean.FALSE, o.output().get("keyFound"));
    }

    @Test
    void unreachableHostFails() {
        // RFC 5737 TEST-NET-1: non-routable, forces a connect/timeout error.
        CheckOutcome o = module.check(context(Map.of(
                "url", "https://192.0.2.1", "timeout", 2)));
        assertFalse(o.online());
        assertEquals(0, ((Number) o.output().get("statusCode")).intValue());
        assertEquals(Boolean.FALSE, o.output().get("jsonValid"));
        assertEquals(Boolean.FALSE, o.output().get("keyFound"));
    }

    // --- live network tests ---

    @Test
    void validJsonResponseIsOnlineWithoutPathCheck() {
        // httpbin.org /get returns well-formed JSON
        CheckOutcome o = module.check(context(Map.of(
                "url", "https://httpbin.org/get",
                "timeout", 10)));

        assumeTrue(((Number) o.output().get("statusCode")).intValue() == 200,
                "httpbin.org not reachable in this environment");

        assertTrue(o.online());
        assertEquals(Boolean.TRUE, o.output().get("jsonValid"));
        assertEquals(Boolean.TRUE, o.output().get("keyFound")); // no path = always true
        assertTrue(o.message().contains("valid JSON"));
    }

    @Test
    void presentTopLevelKeyIsFound() {
        // httpbin.org /get always returns a "url" key at the top level
        CheckOutcome o = module.check(context(Map.of(
                "url", "https://httpbin.org/get",
                "jsonPath", "url",
                "timeout", 10)));

        assumeTrue(((Number) o.output().get("statusCode")).intValue() == 200,
                "httpbin.org not reachable in this environment");

        assertTrue(o.online());
        assertEquals(Boolean.TRUE, o.output().get("jsonValid"));
        assertEquals(Boolean.TRUE, o.output().get("keyFound"));
    }

    @Test
    void nestedKeyPathIsFound() {
        // httpbin.org /get returns { "headers": { "Host": "httpbin.org", ... }, ... }
        CheckOutcome o = module.check(context(Map.of(
                "url", "https://httpbin.org/get",
                "jsonPath", "headers.Host",
                "timeout", 10)));

        assumeTrue(((Number) o.output().get("statusCode")).intValue() == 200,
                "httpbin.org not reachable in this environment");

        assertTrue(o.online());
        assertEquals(Boolean.TRUE, o.output().get("jsonValid"));
        assertEquals(Boolean.TRUE, o.output().get("keyFound"));
    }

    @Test
    void missingKeyPathFails() {
        CheckOutcome o = module.check(context(Map.of(
                "url", "https://httpbin.org/get",
                "jsonPath", "this.key.does.not.exist",
                "timeout", 10)));

        assumeTrue(((Number) o.output().get("statusCode")).intValue() == 200,
                "httpbin.org not reachable in this environment");

        assertFalse(o.online());
        assertEquals(Boolean.TRUE, o.output().get("jsonValid"));
        assertEquals(Boolean.FALSE, o.output().get("keyFound"));
    }

    @Test
    void expectedValueMatchesAndIsOnline() {
        // /get returns { "url": "https://httpbin.org/get" }
        CheckOutcome o = module.check(context(Map.of(
                "url", "https://httpbin.org/get",
                "jsonPath", "url",
                "expectValue", "https://httpbin.org/get",
                "timeout", 10)));

        assumeTrue(((Number) o.output().get("statusCode")).intValue() == 200,
                "httpbin.org not reachable in this environment");

        assertTrue(o.online());
        assertEquals(Boolean.TRUE, o.output().get("jsonValid"));
        assertEquals(Boolean.TRUE, o.output().get("keyFound"));
    }

    @Test
    void wrongExpectedValueFails() {
        CheckOutcome o = module.check(context(Map.of(
                "url", "https://httpbin.org/get",
                "jsonPath", "url",
                "expectValue", "this-is-not-the-right-value",
                "timeout", 10)));

        assumeTrue(((Number) o.output().get("statusCode")).intValue() == 200,
                "httpbin.org not reachable in this environment");

        assertFalse(o.online());
        assertEquals(Boolean.TRUE, o.output().get("jsonValid"));
        assertEquals(Boolean.TRUE, o.output().get("keyFound")); // key exists, value wrong
    }

    @Test
    void nonJsonResponseFails() {
        // example.com returns HTML, not JSON
        CheckOutcome o = module.check(context(Map.of(
                "url", "https://example.com",
                "timeout", 10)));

        assumeTrue(((Number) o.output().get("statusCode")).intValue() == 200,
                "example.com not reachable in this environment");

        assertFalse(o.online());
        assertEquals(Boolean.FALSE, o.output().get("jsonValid"));
        assertEquals(Boolean.FALSE, o.output().get("keyFound"));
        assertTrue(o.message().contains("not valid JSON"));
    }

    @Test
    void unexpectedStatusCodeFails() {
        CheckOutcome o = module.check(context(Map.of(
                "url", "https://httpbin.org/status/404",
                "expectStatus", 200,
                "timeout", 10)));

        assumeTrue(((Number) o.output().get("statusCode")).intValue() != 0,
                "httpbin.org not reachable in this environment");

        assertFalse(o.online());
        assertEquals(404, ((Number) o.output().get("statusCode")).intValue());
        assertEquals(Boolean.FALSE, o.output().get("jsonValid"));
    }

    @Test
    void ghostApiReturnsValidJsonWithSiteTitle() {
        // Live test against curiousmentality.net Ghost admin site endpoint
        CheckOutcome o = module.check(context(Map.of(
                "url", "https://curiousmentality.net/ghost/api/admin/site/",
                "jsonPath", "site.title",
                "timeout", 10)));

        assumeTrue(((Number) o.output().get("statusCode")).intValue() == 200,
                "curiousmentality.net not reachable in this environment");

        assertTrue(o.online());
        assertEquals(Boolean.TRUE, o.output().get("jsonValid"));
        assertEquals(Boolean.TRUE, o.output().get("keyFound"));
    }

    @Test
    void globalLuumoRegionsContainsSerialKey() {
        // Live test against global.luumo.io regions endpoint
        CheckOutcome o = module.check(context(Map.of(
                "url", "https://global.luumo.io/regions",
                "jsonPath", "serial",
                "timeout", 10)));

        assumeTrue(((Number) o.output().get("statusCode")).intValue() == 200,
                "global.luumo.io not reachable in this environment");

        assertTrue(o.online());
        assertEquals(Boolean.TRUE, o.output().get("jsonValid"));
        assertEquals(Boolean.TRUE, o.output().get("keyFound"));
    }

    // --- multi-assertion (jsonChecks) tests ---

    @Test
    void multiCheckAllPassIsOnline() {
        // httpbin.org /get returns { "url": "...", "headers": { "Host": "..." }, ... }
        CheckOutcome o = module.check(context(Map.of(
                "url", "https://httpbin.org/get",
                "jsonChecks", List.of(
                        Map.of("path", "url"),
                        Map.of("path", "headers.Host")),
                "timeout", 10)));

        assumeTrue(((Number) o.output().get("statusCode")).intValue() == 200,
                "httpbin.org not reachable in this environment");

        assertTrue(o.online());
        assertEquals(Boolean.TRUE, o.output().get("jsonValid"));
        assertEquals(Boolean.TRUE, o.output().get("keyFound"));
        assertEquals(Boolean.TRUE, o.output().get("valueMatched"));
        assertTrue(o.message().contains("all JSON checks passed"));
    }

    @Test
    void multiCheckValueMatchAllPassIsOnline() {
        CheckOutcome o = module.check(context(Map.of(
                "url", "https://httpbin.org/get",
                "jsonChecks", List.of(
                        Map.of("path", "url",
                               "expectValue", "https://httpbin.org/get")),
                "timeout", 10)));

        assumeTrue(((Number) o.output().get("statusCode")).intValue() == 200,
                "httpbin.org not reachable in this environment");

        assertTrue(o.online());
        assertEquals(Boolean.TRUE, o.output().get("valueMatched"));
    }

    @Test
    void multiCheckOneMissingKeyFails() {
        CheckOutcome o = module.check(context(Map.of(
                "url", "https://httpbin.org/get",
                "jsonChecks", List.of(
                        Map.of("path", "url"),
                        Map.of("path", "this.key.does.not.exist")),
                "timeout", 10)));

        assumeTrue(((Number) o.output().get("statusCode")).intValue() == 200,
                "httpbin.org not reachable in this environment");

        assertFalse(o.online());
        assertEquals(Boolean.FALSE, o.output().get("keyFound"));
        assertEquals(Boolean.FALSE, o.output().get("valueMatched"));
    }

    @Test
    void multiCheckOneValueMismatchFails() {
        CheckOutcome o = module.check(context(Map.of(
                "url", "https://httpbin.org/get",
                "jsonChecks", List.of(
                        Map.of("path", "url",
                               "expectValue", "https://httpbin.org/get"),
                        Map.of("path", "url",
                               "expectValue", "wrong-value")),
                "timeout", 10)));

        assumeTrue(((Number) o.output().get("statusCode")).intValue() == 200,
                "httpbin.org not reachable in this environment");

        assertFalse(o.online());
        assertEquals(Boolean.TRUE, o.output().get("keyFound"));
        assertEquals(Boolean.FALSE, o.output().get("valueMatched"));
        assertTrue(o.message().contains("wrong-value"));
    }

    @Test
    void multiCheckFailureMessageListsAllFailures() {
        CheckOutcome o = module.check(context(Map.of(
                "url", "https://httpbin.org/get",
                "jsonChecks", List.of(
                        Map.of("path", "missing.one"),
                        Map.of("path", "missing.two")),
                "timeout", 10)));

        assumeTrue(((Number) o.output().get("statusCode")).intValue() == 200,
                "httpbin.org not reachable in this environment");

        assertFalse(o.online());
        assertTrue(o.message().contains("missing.one"));
        assertTrue(o.message().contains("missing.two"));
    }
}
