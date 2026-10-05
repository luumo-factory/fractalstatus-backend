package ai.luumo.fractalstatus.log;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * A single entry in the central log.
 *
 * @param seq     monotonic sequence number (useful for SSE resumption)
 * @param ts      timestamp
 * @param level   severity
 * @param path    node path key this entry relates to (e.g.
 *                {@code root.servers.pveHosts.pve01}); may be {@code null} /
 *                {@code "system"} for application-wide entries
 * @param message log message
 */
public record LogEntry(long seq, Instant ts, LogLevel level, String path, String message) {

    private static final AtomicLong SEQUENCE = new AtomicLong();

    public static LogEntry of(LogLevel level, String path, String message) {
        return new LogEntry(SEQUENCE.incrementAndGet(), Instant.now(), level, path, message);
    }
}
