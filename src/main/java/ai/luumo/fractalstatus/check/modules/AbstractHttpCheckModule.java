package ai.luumo.fractalstatus.check.modules;

import ai.luumo.fractalstatus.check.CheckContext;
import ai.luumo.fractalstatus.check.CheckModule;
import ai.luumo.fractalstatus.model.runtime.Metric;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLException;
import javax.net.ssl.SSLParameters;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpTimeoutException;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Shared machinery for HTTP-based check modules: bounded (timeout-safe) requests,
 * SSL certificate validation, redirect handling, and a cached {@link HttpClient}
 * per distinct configuration.
 *
 * <p>Both the connect timeout and the overall request timeout are always set, so
 * a server that accepts a connection but never responds (or never completes the
 * TLS handshake) is abandoned rather than hanging the monitoring thread
 * indefinitely.
 *
 * <p>Common config keys:
 * <ul>
 *   <li>{@code url} (required) - target URL (supports {@code ${this.*}}).</li>
 *   <li>{@code method} (default {@code GET}).</li>
 *   <li>{@code timeout} (seconds, default 10) - connect and request timeout.</li>
 *   <li>{@code followRedirects} (default true).</li>
 *   <li>{@code verifySsl} (default true) - when false, certificate and hostname
 *       validation are skipped (for internal/self-signed endpoints).</li>
 * </ul>
 */
public abstract class AbstractHttpCheckModule implements CheckModule {

    protected static final int DEFAULT_TIMEOUT_SECONDS = 10;

    private final Map<String, HttpClient> clients = new ConcurrentHashMap<>();

    @Override
    public Metric primaryMetric(Map<String, Object> output) {
        Object status = output.get("statusCode");
        if (status instanceof Number n && n.intValue() != 0) {
            return new Metric(Integer.toString(n.intValue()), "HTTP");
        }
        return null;
    }

    /** Outcome of a single HTTP probe. Fields are always populated (sentinels on error). */
    protected record Probe(int statusCode,
                           double responseTimeMs,
                           boolean sslValid,
                           String body,
                           String error) {
        boolean failed() {
            return error != null;
        }
    }

    /**
     * Performs one HTTP request. Never throws for network/TLS/timeout errors;
     * those are reported via {@link Probe#error()} with {@code statusCode == 0}.
     *
     * @param readBody when true the response body is read into {@link Probe#body()}
     */
    protected Probe probe(CheckContext ctx, boolean readBody) {
        String url = ctx.getString("url", "").trim();
        if (url.isEmpty()) {
            return new Probe(0, 0.0, false, null, "no url configured");
        }
        String method = ctx.getString("method", "GET").toUpperCase(Locale.ROOT);
        int timeout = Math.max(1, ctx.getInt("timeout", DEFAULT_TIMEOUT_SECONDS));
        boolean followRedirects = getBool(ctx, "followRedirects", true);
        boolean verifySsl = getBool(ctx, "verifySsl", true);

        long start = System.nanoTime();
        try {
            HttpClient client = clientFor(timeout, verifySsl, followRedirects);
            HttpRequest.Builder rb = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(timeout))
                    .header("User-Agent", "FractalStatus/1.0");
            if ("GET".equals(method)) {
                rb.GET();
            } else if ("HEAD".equals(method)) {
                rb.method("HEAD", HttpRequest.BodyPublishers.noBody());
            } else {
                rb.method(method, HttpRequest.BodyPublishers.noBody());
            }

            HttpResponse<?> response = readBody
                    ? client.send(rb.build(), HttpResponse.BodyHandlers.ofString())
                    : client.send(rb.build(), HttpResponse.BodyHandlers.discarding());
            String body = readBody ? (String) response.body() : null;
            return new Probe(response.statusCode(), elapsedMs(start), true, body, null);
        } catch (HttpTimeoutException e) {
            return new Probe(0, elapsedMs(start), true, null, "timeout after " + timeout + "s");
        } catch (SSLException e) {
            return new Probe(0, elapsedMs(start), false, null, "SSL error: " + rootMessage(e));
        } catch (ConnectException e) {
            return new Probe(0, elapsedMs(start), true, null, "connection refused: " + rootMessage(e));
        } catch (IOException e) {
            boolean ssl = hasSslCause(e);
            return new Probe(0, elapsedMs(start), !ssl,
                    null, (ssl ? "SSL error: " : "I/O error: ") + rootMessage(e));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Probe(0, elapsedMs(start), true, null, "interrupted");
        } catch (RuntimeException e) {
            return new Probe(0, elapsedMs(start), true, null, "invalid request: " + rootMessage(e));
        }
    }

    protected static boolean getBool(CheckContext ctx, String key, boolean defaultValue) {
        Object v = ctx.config().get(key);
        if (v instanceof Boolean b) {
            return b;
        }
        if (v instanceof String s && !s.isBlank()) {
            return Boolean.parseBoolean(s.trim());
        }
        return defaultValue;
    }

    private HttpClient clientFor(int timeout, boolean verifySsl, boolean followRedirects) {
        String key = timeout + "|" + verifySsl + "|" + followRedirects;
        return clients.computeIfAbsent(key, k -> buildClient(timeout, verifySsl, followRedirects));
    }

    private HttpClient buildClient(int timeout, boolean verifySsl, boolean followRedirects) {
        HttpClient.Builder builder = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(timeout))
                .followRedirects(followRedirects
                        ? HttpClient.Redirect.NORMAL
                        : HttpClient.Redirect.NEVER);
        if (!verifySsl) {
            try {
                SSLContext sc = SSLContext.getInstance("TLS");
                sc.init(null, TRUST_ALL, new SecureRandom());
                builder.sslContext(sc);
                SSLParameters params = new SSLParameters();
                params.setEndpointIdentificationAlgorithm(null); // disable hostname check
                builder.sslParameters(params);
            } catch (GeneralSecurityException e) {
                throw new IllegalStateException("Failed to build insecure SSL context", e);
            }
        }
        return builder.build();
    }

    private static double elapsedMs(long startNanos) {
        return Math.round((System.nanoTime() - startNanos) / 1_000_000.0 * 1000.0) / 1000.0;
    }

    private static boolean hasSslCause(Throwable t) {
        for (Throwable c = t; c != null; c = c.getCause()) {
            if (c instanceof SSLException) {
                return true;
            }
        }
        return false;
    }

    private static String rootMessage(Throwable t) {
        Throwable c = t;
        while (c.getCause() != null && c.getCause() != c) {
            c = c.getCause();
        }
        String msg = c.getMessage();
        return msg == null ? c.getClass().getSimpleName() : msg;
    }

    private static final TrustManager[] TRUST_ALL = {
            new X509TrustManager() {
                public void checkClientTrusted(X509Certificate[] chain, String authType) {
                }

                public void checkServerTrusted(X509Certificate[] chain, String authType) {
                }

                public X509Certificate[] getAcceptedIssuers() {
                    return new X509Certificate[0];
                }
            }
    };
}
