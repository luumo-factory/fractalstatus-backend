package ai.luumo.fractalstatus.check.modules;

import ai.luumo.fractalstatus.check.CheckContext;
import ai.luumo.fractalstatus.check.CheckModule;
import ai.luumo.fractalstatus.check.CheckOutcome;
import ai.luumo.fractalstatus.model.runtime.Metric;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Pings a host by invoking the operating system's {@code ping} command and
 * reports reachability, round-trip time and packet loss.
 *
 * <p>Config:
 * <ul>
 *   <li>{@code host} (required) - hostname or IP to ping.</li>
 *   <li>{@code count} (optional, default 1) - echo requests to send.</li>
 *   <li>{@code timeoutMs} (optional) - per-reply wait in milliseconds. Takes
 *       precedence over {@code timeout}.</li>
 *   <li>{@code timeout} (optional, default 2) - per-reply wait in seconds; may be
 *       fractional (e.g. 0.2). Used when {@code timeoutMs} is absent.</li>
 * </ul>
 *
 * <p>Output fields: {@code reachable} (boolean), {@code rttMs} (number, average;
 * present only when reachable), {@code packetLoss} (number, percent).
 *
 * <p>The command is executed via {@link ProcessBuilder} with an argument list
 * (no shell), so the host argument is not subject to shell injection.
 */
@Component
public class PingCheckModule implements CheckModule {

    private static final int DEFAULT_COUNT = 1;
    private static final int DEFAULT_TIMEOUT_MS = 2000;
    private static final int MIN_TIMEOUT_MS = 1;

    private static final Pattern RTT_SUMMARY =
            Pattern.compile("=\\s*[\\d.]+/([\\d.]+)/[\\d.]+");
    private static final Pattern RTT_INLINE =
            Pattern.compile("time[=<]\\s*([\\d.]+)\\s*ms");
    private static final Pattern PACKET_LOSS =
            Pattern.compile("([\\d.]+)%\\s*packet loss");

    @Override
    public String id() {
        return "ping";
    }

    @Override
    public Metric primaryMetric(java.util.Map<String, Object> output) {
        Object rtt = output.get("rttMs");
        if (rtt instanceof Number n) {
            return new Metric(String.format(Locale.ROOT, "%.2f", n.doubleValue()), "ms");
        }
        return null;
    }

    @Override
    public CheckOutcome check(CheckContext ctx) throws Exception {
        String host = ctx.getString("host", "").trim();
        if (host.isEmpty()) {
            return CheckOutcome.builder(false)
                    .put("reachable", false)
                    .message("no host configured")
                    .build();
        }
        int count = Math.max(1, ctx.getInt("count", DEFAULT_COUNT));
        int timeoutMs = timeoutMillis(ctx);

        List<String> command = buildCommand(host, count, timeoutMs);
        ProcessBuilder pb = new ProcessBuilder(command).redirectErrorStream(true);

        long overallMs = (long) count * timeoutMs + 2000;
        Process process = pb.start();
        boolean finished = process.waitFor(overallMs, TimeUnit.MILLISECONDS);
        if (!finished) {
            process.destroyForcibly();
            process.waitFor(2, TimeUnit.SECONDS);
        }
        String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
        int exit = finished ? process.exitValue() : -1;

        boolean reachable = finished && exit == 0;
        Double rttMs = parseRtt(output);
        Double packetLoss = parsePacketLoss(output);

        CheckOutcome.Builder outcome = CheckOutcome.builder(reachable)
                .put("reachable", reachable);
        if (packetLoss != null) {
            outcome.put("packetLoss", packetLoss);
        }
        if (reachable && rttMs != null) {
            outcome.put("rttMs", rttMs);
            outcome.message(String.format(Locale.ROOT, "%s reachable, rtt %.3f ms", host, rttMs));
        } else if (!finished) {
            outcome.message(host + " ping timed out");
        } else {
            outcome.message(host + " unreachable");
        }
        ctx.log().debug("ping " + host + " -> reachable=" + reachable
                + (rttMs != null ? ", rttMs=" + rttMs : ""));
        return outcome.build();
    }

    private List<String> buildCommand(String host, int count, int timeoutMs) {
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        List<String> cmd = new ArrayList<>();
        cmd.add("ping");
        if (os.contains("win")) {
            cmd.add("-n");
            cmd.add(Integer.toString(count));
            cmd.add("-w");
            cmd.add(Integer.toString(timeoutMs)); // milliseconds on Windows
        } else if (os.contains("mac") || os.contains("darwin")) {
            cmd.add("-c");
            cmd.add(Integer.toString(count));
            cmd.add("-W");
            cmd.add(Integer.toString(timeoutMs)); // milliseconds on macOS
        } else {
            cmd.add("-c");
            cmd.add(Integer.toString(count));
            cmd.add("-W");
            cmd.add(secondsArg(timeoutMs)); // seconds (fractional allowed) on Linux
        }
        cmd.add(host);
        return cmd;
    }

    /**
     * Per-reply timeout in milliseconds. Uses {@code timeoutMs} if present,
     * otherwise {@code timeout} seconds (which may be fractional), else the
     * default.
     */
    private int timeoutMillis(CheckContext ctx) {
        Object ms = ctx.config().get("timeoutMs");
        Integer fromMs = toMillis(ms, 1.0);
        if (fromMs != null) {
            return Math.max(MIN_TIMEOUT_MS, fromMs);
        }
        Object secs = ctx.config().get("timeout");
        Integer fromSecs = toMillis(secs, 1000.0);
        if (fromSecs != null) {
            return Math.max(MIN_TIMEOUT_MS, fromSecs);
        }
        return DEFAULT_TIMEOUT_MS;
    }

    private Integer toMillis(Object value, double scale) {
        if (value instanceof Number n) {
            return (int) Math.round(n.doubleValue() * scale);
        }
        if (value instanceof String s && !s.isBlank()) {
            try {
                return (int) Math.round(Double.parseDouble(s.trim()) * scale);
            } catch (NumberFormatException ignored) {
                return null;
            }
        }
        return null;
    }

    /** Formats a millisecond timeout as a seconds argument for Linux ping (e.g. 200 -> "0.2"). */
    private String secondsArg(int timeoutMs) {
        if (timeoutMs % 1000 == 0) {
            return Integer.toString(timeoutMs / 1000);
        }
        return Double.toString(timeoutMs / 1000.0);
    }

    /** Average RTT in ms: prefers the summary line, falls back to the first reply. */
    private Double parseRtt(String output) {
        Matcher summary = RTT_SUMMARY.matcher(output);
        if (summary.find()) {
            return parseDouble(summary.group(1));
        }
        Matcher inline = RTT_INLINE.matcher(output);
        if (inline.find()) {
            return parseDouble(inline.group(1));
        }
        return null;
    }

    private Double parsePacketLoss(String output) {
        Matcher m = PACKET_LOSS.matcher(output);
        return m.find() ? parseDouble(m.group(1)) : null;
    }

    private Double parseDouble(String s) {
        try {
            return Double.parseDouble(s);
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
