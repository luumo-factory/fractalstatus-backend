package ai.luumo.fractalstatus.log;

/**
 * A thin, path-scoped facade over {@link LogStore} handed to check modules and
 * internal components so every entry is tagged with the node path it concerns.
 */
public final class NodeLogger {

    private final LogStore store;
    private final String path;

    public NodeLogger(LogStore store, String path) {
        this.store = store;
        this.path = path;
    }

    public void debug(String message) {
        store.log(LogLevel.DEBUG, path, message);
    }

    public void info(String message) {
        store.log(LogLevel.INFO, path, message);
    }

    public void warn(String message) {
        store.log(LogLevel.WARN, path, message);
    }

    public void error(String message) {
        store.log(LogLevel.ERROR, path, message);
    }

    public String path() {
        return path;
    }
}
