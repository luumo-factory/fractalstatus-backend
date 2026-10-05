package ai.luumo.fractalstatus.check.modules;

import ai.luumo.fractalstatus.check.CheckContext;
import ai.luumo.fractalstatus.check.CheckOutcome;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

/**
 * Checks an HTTP(S) endpoint for an expected status code AND that the response
 * body matches an expected value, with a valid SSL certificate.
 *
 * <p>Config (plus the common keys in {@link AbstractHttpCheckModule}):
 * <ul>
 *   <li>{@code expectStatus} (optional, default 200).</li>
 *   <li>{@code expectBody} (required for a meaningful check) - the value to match.</li>
 *   <li>{@code matchMode} (optional, default {@code contains}) - one of
 *       {@code contains}, {@code equals}, {@code regex}.</li>
 *   <li>{@code ignoreCase} (optional, default false).</li>
 * </ul>
 *
 * <p>Output fields (always present): {@code statusCode} (int), {@code responseTimeMs}
 * (number), {@code sslValid} (boolean), {@code bodyMatched} (boolean).
 */
@Component
public class HttpBodyCheckModule extends AbstractHttpCheckModule {

    @Override
    public String id() {
        return "http-body";
    }

    @Override
    public CheckOutcome check(CheckContext ctx) {
        int expect = ctx.getInt("expectStatus", 200);
        String expectBody = ctx.getString("expectBody", "");
        String matchMode = ctx.getString("matchMode", "contains").toLowerCase(Locale.ROOT);
        boolean ignoreCase = getBool(ctx, "ignoreCase", false);

        Probe probe = probe(ctx, true);

        boolean statusOk = !probe.failed() && probe.statusCode() == expect;
        boolean bodyMatched = !probe.failed()
                && probe.body() != null
                && matches(probe.body(), expectBody, matchMode, ignoreCase);
        boolean online = statusOk && probe.sslValid() && bodyMatched;

        String message;
        if (probe.failed()) {
            message = probe.error();
        } else if (!probe.sslValid()) {
            message = "HTTP " + probe.statusCode() + " (invalid SSL certificate)";
        } else if (!statusOk) {
            message = "HTTP " + probe.statusCode() + " (expected " + expect + ")";
        } else if (!bodyMatched) {
            message = "HTTP " + probe.statusCode() + " but body did not match ("
                    + matchMode + ")";
        } else {
            message = "HTTP " + probe.statusCode() + ", body matched";
        }

        ctx.log().debug("http-body " + ctx.getString("url", "") + " -> "
                + (probe.failed() ? probe.error()
                        : probe.statusCode() + " bodyMatched=" + bodyMatched));

        return CheckOutcome.builder(online)
                .put("statusCode", probe.statusCode())
                .put("responseTimeMs", probe.responseTimeMs())
                .put("sslValid", probe.sslValid())
                .put("bodyMatched", bodyMatched)
                .message(message)
                .build();
    }

    private boolean matches(String body, String expect, String mode, boolean ignoreCase) {
        if (expect == null || expect.isEmpty()) {
            return false;
        }
        switch (mode) {
            case "equals":
                return ignoreCase
                        ? body.trim().equalsIgnoreCase(expect.trim())
                        : body.trim().equals(expect.trim());
            case "regex":
                try {
                    int flags = ignoreCase ? Pattern.CASE_INSENSITIVE : 0;
                    return Pattern.compile(expect, flags).matcher(body).find();
                } catch (PatternSyntaxException e) {
                    return false;
                }
            case "contains":
            default:
                return ignoreCase
                        ? body.toLowerCase(Locale.ROOT).contains(expect.toLowerCase(Locale.ROOT))
                        : body.contains(expect);
        }
    }
}
