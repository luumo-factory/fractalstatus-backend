package ai.luumo.fractalstatus.check.modules;

import ai.luumo.fractalstatus.check.CheckContext;
import ai.luumo.fractalstatus.check.CheckOutcome;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Checks that an HTTP(S) endpoint returns a valid JSON response, with optional
 * assertions against the parsed body.
 *
 * <p>Two assertion modes (mutually exclusive; {@code jsonChecks} takes priority):
 *
 * <p><b>Single assertion</b> - original behaviour:
 * <ul>
 *   <li>{@code jsonPath} - dot-separated path, e.g. {@code site.title} or
 *       {@code serial}. Omit to accept any valid JSON.</li>
 *   <li>{@code expectValue} - string the value at {@code jsonPath} must equal
 *       exactly. Omit to only require the key to exist.</li>
 * </ul>
 *
 * <p><b>Multi assertion</b> - for checking several fields at once:
 * <pre>
 * "jsonChecks": [
 *   { "path": "results.is_connected", "expectValue": "true" },
 *   { "path": "results.is_logged_in", "expectValue": "true" }
 * ]
 * </pre>
 * All entries must pass for the check to be online. Each entry follows the same
 * dot-separated path and string-comparison rules as the single mode.
 * {@code expectValue} is optional per entry (key-exists is sufficient if omitted).
 *
 * <p>Common config (plus keys in {@link AbstractHttpCheckModule}):
 * <ul>
 *   <li>{@code expectStatus} (default 200)</li>
 * </ul>
 *
 * <p>Output fields (always present):
 * <ul>
 *   <li>{@code statusCode}, {@code responseTimeMs}, {@code sslValid}</li>
 *   <li>{@code jsonValid} (boolean)</li>
 *   <li>{@code keyFound} (boolean) - all asserted paths exist</li>
 *   <li>{@code valueMatched} (boolean) - all value assertions passed</li>
 * </ul>
 */
@Component
public class HttpJsonCheckModule extends AbstractHttpCheckModule {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public String id() {
        return "http-json";
    }

    @Override
    public CheckOutcome check(CheckContext ctx) {
        int expect = ctx.getInt("expectStatus", 200);

        Probe probe = probe(ctx, true);

        if (probe.failed()) {
            return failure(probe, false, false, probe.error());
        }
        if (!probe.sslValid()) {
            return failure(probe, false, false,
                    "HTTP " + probe.statusCode() + " (invalid SSL certificate)");
        }
        if (probe.statusCode() != expect) {
            return failure(probe, false, false,
                    "HTTP " + probe.statusCode() + " (expected " + expect + ")");
        }

        // --- parse JSON ---
        JsonNode root;
        try {
            root = MAPPER.readTree(probe.body() == null ? "" : probe.body());
        } catch (Exception e) {
            ctx.log().debug("http-json " + ctx.getString("url", "")
                    + " -> JSON parse failed: " + e.getMessage());
            return failure(probe, false, false,
                    "HTTP " + probe.statusCode() + " but response is not valid JSON");
        }

        // --- multi-assertion mode ---
        Object jsonChecksRaw = ctx.config().get("jsonChecks");
        if (jsonChecksRaw instanceof List<?> list && !list.isEmpty()) {
            return runMultiChecks(ctx, probe, root, list);
        }

        // --- single-assertion mode ---
        String jsonPath = ctx.getString("jsonPath", "").trim();
        String expectValue = ctx.getString("expectValue", "").trim();

        if (jsonPath.isEmpty()) {
            ctx.log().debug("http-json " + ctx.getString("url", "")
                    + " -> " + probe.statusCode() + " valid JSON");
            return success(probe, "HTTP " + probe.statusCode() + ", valid JSON");
        }

        JsonNode node = navigatePath(root, jsonPath);
        boolean keyFound = isPresent(node);

        if (!keyFound) {
            ctx.log().debug("http-json " + ctx.getString("url", "")
                    + " -> key '" + jsonPath + "' not found");
            return CheckOutcome.builder(false)
                    .put("statusCode", probe.statusCode())
                    .put("responseTimeMs", probe.responseTimeMs())
                    .put("sslValid", probe.sslValid())
                    .put("jsonValid", true)
                    .put("keyFound", false)
                    .put("valueMatched", false)
                    .message("HTTP " + probe.statusCode()
                            + ", valid JSON but key '" + jsonPath + "' not found")
                    .build();
        }

        if (!expectValue.isEmpty()) {
            String actual = textOf(node);
            boolean matched = actual.equals(expectValue);
            String msg = matched
                    ? "HTTP " + probe.statusCode() + ", '" + jsonPath + "' = '" + expectValue + "'"
                    : "HTTP " + probe.statusCode() + ", '" + jsonPath + "' expected '"
                            + expectValue + "' but got '" + actual + "'";
            ctx.log().debug("http-json " + ctx.getString("url", "")
                    + " -> '" + jsonPath + "' valueMatched=" + matched);
            return CheckOutcome.builder(matched)
                    .put("statusCode", probe.statusCode())
                    .put("responseTimeMs", probe.responseTimeMs())
                    .put("sslValid", probe.sslValid())
                    .put("jsonValid", true)
                    .put("keyFound", true)
                    .put("valueMatched", matched)
                    .message(msg)
                    .build();
        }

        ctx.log().debug("http-json " + ctx.getString("url", "")
                + " -> " + probe.statusCode() + " key '" + jsonPath + "' present");
        return CheckOutcome.builder(true)
                .put("statusCode", probe.statusCode())
                .put("responseTimeMs", probe.responseTimeMs())
                .put("sslValid", probe.sslValid())
                .put("jsonValid", true)
                .put("keyFound", true)
                .put("valueMatched", true)
                .message("HTTP " + probe.statusCode() + ", key '" + jsonPath + "' present")
                .build();
    }

    // --- multi-assertion ---

    @SuppressWarnings("unchecked")
    private CheckOutcome runMultiChecks(CheckContext ctx, Probe probe,
            JsonNode root, List<?> checks) {
        boolean allKeysFound = true;
        boolean allValuesMatched = true;
        List<String> failures = new ArrayList<>();

        for (Object item : checks) {
            if (!(item instanceof Map<?, ?> entry)) {
                continue;
            }
            Map<String, Object> check = (Map<String, Object>) entry;
            String path = String.valueOf(check.getOrDefault("path", "")).trim();
            if (path.isEmpty()) {
                continue;
            }
            String expectValue = check.containsKey("expectValue")
                    ? String.valueOf(check.get("expectValue")).trim()
                    : "";

            JsonNode node = navigatePath(root, path);
            if (!isPresent(node)) {
                allKeysFound = false;
                allValuesMatched = false;
                failures.add("'" + path + "' not found");
                continue;
            }

            if (!expectValue.isEmpty()) {
                String actual = textOf(node);
                if (!actual.equals(expectValue)) {
                    allValuesMatched = false;
                    failures.add("'" + path + "' expected '" + expectValue
                            + "' but got '" + actual + "'");
                }
            }
        }

        boolean online = allKeysFound && allValuesMatched;
        String message = online
                ? "HTTP " + probe.statusCode() + ", all JSON checks passed"
                : "HTTP " + probe.statusCode() + ": " + String.join("; ", failures);

        ctx.log().debug("http-json " + ctx.getString("url", "")
                + " -> multi-check online=" + online
                + (failures.isEmpty() ? "" : " failures=" + failures));

        return CheckOutcome.builder(online)
                .put("statusCode", probe.statusCode())
                .put("responseTimeMs", probe.responseTimeMs())
                .put("sslValid", probe.sslValid())
                .put("jsonValid", true)
                .put("keyFound", allKeysFound)
                .put("valueMatched", allValuesMatched)
                .message(message)
                .build();
    }

    // --- helpers ---

    private static CheckOutcome failure(Probe probe, boolean jsonValid,
            boolean keyFound, String message) {
        return CheckOutcome.builder(false)
                .put("statusCode", probe.statusCode())
                .put("responseTimeMs", probe.responseTimeMs())
                .put("sslValid", probe.sslValid())
                .put("jsonValid", jsonValid)
                .put("keyFound", keyFound)
                .put("valueMatched", false)
                .message(message)
                .build();
    }

    private static CheckOutcome success(Probe probe, String message) {
        return CheckOutcome.builder(true)
                .put("statusCode", probe.statusCode())
                .put("responseTimeMs", probe.responseTimeMs())
                .put("sslValid", probe.sslValid())
                .put("jsonValid", true)
                .put("keyFound", true)
                .put("valueMatched", true)
                .message(message)
                .build();
    }

    private static boolean isPresent(JsonNode node) {
        return node != null && !node.isMissingNode() && !node.isNull();
    }

    private static String textOf(JsonNode node) {
        return node.isTextual() ? node.asText() : node.toString();
    }

    /**
     * Walks a dot-separated path through a JSON object tree.
     * Returns {@code null} if any segment is missing or an intermediate node is
     * not an object.
     */
    private static JsonNode navigatePath(JsonNode root, String path) {
        JsonNode current = root;
        for (String segment : path.split("\\.")) {
            if (current == null || !current.isObject()) {
                return null;
            }
            current = current.get(segment);
        }
        return current;
    }
}
