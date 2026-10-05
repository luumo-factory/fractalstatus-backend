package ai.luumo.fractalstatus.api;

import ai.luumo.fractalstatus.check.CheckContext;
import ai.luumo.fractalstatus.check.CheckOutcome;
import ai.luumo.fractalstatus.check.modules.WebhookCheckModule;
import ai.luumo.fractalstatus.check.modules.WebhookStore;
import ai.luumo.fractalstatus.interpolation.ValueInterpolator;
import ai.luumo.fractalstatus.log.LogStore;
import ai.luumo.fractalstatus.log.NodeLogger;
import ai.luumo.fractalstatus.model.EntityNode;
import ai.luumo.fractalstatus.model.Node;
import ai.luumo.fractalstatus.model.config.CheckConfig;
import ai.luumo.fractalstatus.model.runtime.CheckResult;
import ai.luumo.fractalstatus.state.TreeStateUpdater;
import ai.luumo.fractalstatus.tree.MonitoringTree;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Receives inbound webhook calls that drive {@link WebhookCheckModule} check
 * instances.
 *
 * <p>URL format: {@code /webhooks/<seg1>/<seg2>/.../<checkName>}
 *
 * <p>The path segments before the final one are joined with {@code .} to form
 * the entity path suffix (the root node ID may be omitted). For example, an
 * entity at {@code root.web.luumo.globalLuumo} with a check named
 * {@code heartbeat} can be pinged at:
 * <pre>
 *   GET  /webhooks/web/luumo/globalLuumo/heartbeat
 *   POST /webhooks/web/luumo/globalLuumo/heartbeat
 * </pre>
 *
 * <p>Both GET and POST are accepted. For {@code mode: json} checks, the POST
 * body must be valid JSON (see {@link WebhookCheckModule}).
 *
 * <p>If the check is configured with a {@code secret}, every request must
 * supply that value in the {@code X-Webhook-Secret} header; otherwise the
 * request is rejected with HTTP 403.
 *
 * <p>On a valid request, the controller:
 * <ol>
 *   <li>Records the ping (and optional payload) in {@link WebhookStore}.</li>
 *   <li>Derives a fresh {@link CheckResult} by invoking the module's
 *       {@code check()} logic immediately.</li>
 *   <li>Propagates the updated state through the monitoring tree so the UI
 *       reflects the change without waiting for the next scheduler tick.</li>
 * </ol>
 */
@RestController
public class WebhookController {

    private static final Logger LOG = LoggerFactory.getLogger(WebhookController.class);
    private static final String SECRET_HEADER = "X-Webhook-Secret";
    private static final String WEBHOOKS_PREFIX = "/webhooks/";

    private final MonitoringTree tree;
    private final WebhookStore store;
    private final WebhookCheckModule module;
    private final TreeStateUpdater stateUpdater;
    private final LogStore logStore;
    private final ObjectMapper objectMapper;

    public WebhookController(MonitoringTree tree,
                              WebhookStore store,
                              WebhookCheckModule module,
                              TreeStateUpdater stateUpdater,
                              LogStore logStore,
                              ObjectMapper objectMapper) {
        this.tree = tree;
        this.store = store;
        this.module = module;
        this.stateUpdater = stateUpdater;
        this.logStore = logStore;
        this.objectMapper = objectMapper;
    }

    @RequestMapping(value = "/webhooks/**",
            method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> receive(HttpServletRequest request)
            throws IOException {

        // --- extract entity path and check name from URL ---
        String uri = request.getRequestURI();
        int prefixIdx = uri.indexOf(WEBHOOKS_PREFIX);
        if (prefixIdx < 0) {
            return error(HttpStatus.BAD_REQUEST, "malformed webhook URL");
        }
        String tail = uri.substring(prefixIdx + WEBHOOKS_PREFIX.length());
        String[] segments = tail.split("/");
        if (segments.length < 2) {
            return error(HttpStatus.BAD_REQUEST,
                    "webhook URL must have at least two segments: <entity-path>/<checkName>");
        }

        String checkName = segments[segments.length - 1];
        String pathSuffix = String.join(".", Arrays.copyOf(segments, segments.length - 1));

        // --- resolve entity - try exact match, then suffix match (root ID may be omitted) ---
        EntityNode entity = resolveEntity(pathSuffix);
        if (entity == null) {
            return error(HttpStatus.NOT_FOUND, "no entity found for path: " + pathSuffix);
        }
        String entityPath = entity.getPath();

        // --- find the webhook check config on that entity ---
        CheckConfig checkCfg = entity.getConfig().getChecks().stream()
                .filter(c -> checkName.equals(c.getName())
                        && "webhook".equals(c.getModule()))
                .findFirst()
                .orElse(null);
        if (checkCfg == null) {
            return error(HttpStatus.NOT_FOUND,
                    "no webhook check named '" + checkName + "' on entity " + entityPath);
        }

        // --- resolve config (interpolate ${this.*} placeholders) ---
        Map<String, Object> cfg = ValueInterpolator.resolve(
                checkCfg.getConfig(), entity.getConfig().getValues());

        // --- validate secret ---
        Object secretObj = cfg.get("secret");
        String secret = secretObj == null ? "" : String.valueOf(secretObj).trim();
        if (!secret.isEmpty()) {
            String provided = request.getHeader(SECRET_HEADER);
            if (!secret.equals(provided)) {
                LOG.warn("webhook {}/{} rejected: invalid or missing {} header",
                        entityPath, checkName, SECRET_HEADER);
                return error(HttpStatus.FORBIDDEN,
                        "invalid or missing " + SECRET_HEADER + " header");
            }
        }

        // --- read and validate body for json mode ---
        String mode = cfg.containsKey("mode")
                ? String.valueOf(cfg.get("mode")).toLowerCase(Locale.ROOT)
                : "ping";
        Map<String, Object> payload = null;

        byte[] bodyBytes = request.getInputStream().readAllBytes();
        String rawBody = bodyBytes.length > 0
                ? new String(bodyBytes, StandardCharsets.UTF_8).trim()
                : null;

        if ("json".equals(mode)) {
            if (rawBody == null || rawBody.isEmpty()) {
                // Body is absent - still record a ping but let the module report
                // that no payload was provided.
                store.recordPing(entityPath, checkName);
            } else {
                try {
                    @SuppressWarnings("unchecked")
                    Map<String, Object> parsed =
                            objectMapper.readValue(rawBody, Map.class);
                    payload = parsed;
                    store.recordPayload(entityPath, checkName, payload);
                } catch (Exception e) {
                    return error(HttpStatus.BAD_REQUEST,
                            "invalid JSON payload: " + e.getMessage());
                }
            }
        } else {
            // Ping mode: any request body is ignored.
            store.recordPing(entityPath, checkName);
        }

        // --- immediately compute and propagate the new check state ---
        NodeLogger nodeLogger = new NodeLogger(logStore, entityPath);
        Map<String, Object> finalCfg = cfg;
        CheckContext ctx = new CheckContext() {
            public Map<String, Object> config()  { return finalCfg; }
            public String nodePath()             { return entityPath; }
            public String checkName()            { return checkName; }
            public NodeLogger log()              { return nodeLogger; }
        };

        CheckOutcome outcome = module.check(ctx);

        CheckResult result = new CheckResult(checkName);
        result.setLastUpdate(Instant.now());
        result.setOnline(outcome.online());
        result.setOutput(new LinkedHashMap<>(outcome.output()));
        result.setMessage(outcome.message());
        entity.getRuntime().putResult(result);
        stateUpdater.onCheckUpdated(entity);

        LOG.debug("webhook {}/{} received, online={}", entityPath, checkName, outcome.online());

        Map<String, Object> response = new LinkedHashMap<>();
        response.put("received", true);
        response.put("path", entityPath);
        response.put("check", checkName);
        response.put("online", outcome.online());
        return ResponseEntity.ok(response);
    }

    /**
     * Finds the entity whose full path equals {@code suffix} exactly, or whose
     * full path ends with {@code "." + suffix} (allowing the caller to omit the
     * root node ID). Returns {@code null} when no match is found.
     */
    private EntityNode resolveEntity(String suffix) {
        // Exact match first (caller supplied the full path).
        Node exact = tree.findByPath(suffix);
        if (exact instanceof EntityNode e) {
            return e;
        }
        // Suffix match - allows omitting the root node ID from the URL.
        String dotSuffix = "." + suffix;
        return tree.getEntities().stream()
                .filter(e -> e.getPath() != null
                        && (e.getPath().equals(suffix) || e.getPath().endsWith(dotSuffix)))
                .findFirst()
                .orElse(null);
    }

    private static ResponseEntity<Map<String, Object>> error(HttpStatus status, String message) {
        return ResponseEntity.status(status).body(Map.of("error", message));
    }
}
