package ai.luumo.fractalstatus.log;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.function.Consumer;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Central, in-memory, bounded log.
 *
 * <p>Entries are kept in a ring buffer and can be queried by node-path prefix
 * (so a request for {@code root.servers} returns everything beneath it).
 * Subscribers (e.g. SSE emitters) receive new matching entries live.
 */
@Component
public class LogStore {

    /** Path value used for application-wide entries not tied to a tree node. */
    public static final String SYSTEM_PATH = "system";

    private final int capacity;
    private final Deque<LogEntry> buffer;
    private final List<Subscriber> subscribers = new CopyOnWriteArrayList<>();

    public LogStore(@Value("${fractalstatus.log.capacity:5000}") int capacity) {
        this.capacity = capacity;
        this.buffer = new ArrayDeque<>(capacity);
    }

    /** Appends an entry and notifies matching subscribers. */
    public void append(LogEntry entry) {
        synchronized (buffer) {
            if (buffer.size() >= capacity) {
                buffer.pollFirst();
            }
            buffer.addLast(entry);
        }
        for (Subscriber s : subscribers) {
            if (matches(entry.path(), s.pathPrefix())) {
                try {
                    s.consumer().accept(entry);
                } catch (RuntimeException ex) {
                    subscribers.remove(s);
                }
            }
        }
    }

    public void log(LogLevel level, String path, String message) {
        append(LogEntry.of(level, path, message));
    }

    /**
     * Returns a snapshot of recent entries matching {@code pathPrefix} (null or
     * blank means all), newest last, limited to {@code limit} entries.
     */
    public List<LogEntry> query(String pathPrefix, int limit) {
        List<LogEntry> snapshot;
        synchronized (buffer) {
            snapshot = new ArrayList<>(buffer);
        }
        List<LogEntry> matched = new ArrayList<>();
        for (LogEntry e : snapshot) {
            if (matches(e.path(), pathPrefix)) {
                matched.add(e);
            }
        }
        if (limit > 0 && matched.size() > limit) {
            return new ArrayList<>(matched.subList(matched.size() - limit, matched.size()));
        }
        return matched;
    }

    /** Registers a live subscriber; returns a handle to unsubscribe. */
    public Subscription subscribe(String pathPrefix, Consumer<LogEntry> consumer) {
        Subscriber subscriber = new Subscriber(pathPrefix, consumer);
        subscribers.add(subscriber);
        return () -> subscribers.remove(subscriber);
    }

    /**
     * A path matches a prefix when it equals the prefix or is nested beneath it
     * (prefix followed by a dot). A null/blank prefix matches everything.
     */
    static boolean matches(String path, String prefix) {
        if (prefix == null || prefix.isBlank()) {
            return true;
        }
        if (path == null) {
            return false;
        }
        return path.equals(prefix) || path.startsWith(prefix + ".");
    }

    @FunctionalInterface
    public interface Subscription {
        void cancel();
    }

    private record Subscriber(String pathPrefix, Consumer<LogEntry> consumer) {
    }
}
