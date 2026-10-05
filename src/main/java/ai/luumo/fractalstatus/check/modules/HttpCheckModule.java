package ai.luumo.fractalstatus.check.modules;

import ai.luumo.fractalstatus.check.CheckContext;
import ai.luumo.fractalstatus.check.CheckOutcome;
import org.springframework.stereotype.Component;

/**
 * Checks that an HTTP(S) endpoint returns an expected status code with a valid
 * SSL certificate.
 *
 * <p>Config (plus the common keys in {@link AbstractHttpCheckModule}):
 * <ul>
 *   <li>{@code expectStatus} (optional, default 200) - the healthy status code.</li>
 * </ul>
 *
 * <p>Output fields (always present): {@code statusCode} (int; 0 on
 * connect/timeout/SSL error), {@code responseTimeMs} (number),
 * {@code sslValid} (boolean).
 */
@Component
public class HttpCheckModule extends AbstractHttpCheckModule {

    @Override
    public String id() {
        return "http";
    }

    @Override
    public CheckOutcome check(CheckContext ctx) {
        int expect = ctx.getInt("expectStatus", 200);
        Probe probe = probe(ctx, false);

        boolean online = !probe.failed() && probe.statusCode() == expect && probe.sslValid();

        String message;
        if (probe.failed()) {
            message = probe.error();
        } else if (online) {
            message = "HTTP " + probe.statusCode();
        } else if (!probe.sslValid()) {
            message = "HTTP " + probe.statusCode() + " (invalid SSL certificate)";
        } else {
            message = "HTTP " + probe.statusCode() + " (expected " + expect + ")";
        }

        ctx.log().debug("http " + ctx.getString("url", "") + " -> "
                + (probe.failed() ? probe.error() : probe.statusCode()));

        return CheckOutcome.builder(online)
                .put("statusCode", probe.statusCode())
                .put("responseTimeMs", probe.responseTimeMs())
                .put("sslValid", probe.sslValid())
                .message(message)
                .build();
    }
}
