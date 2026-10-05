package ai.luumo.fractalstatus;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Application configuration bound from the {@code fractalstatus.*} namespace.
 */
@ConfigurationProperties(prefix = "fractalstatus")
public class FractalstatusProperties {

    /**
     * Location of the monitoring config JSON. Spring resource syntax, e.g.
     * {@code file:/etc/fractalstatus/config.json} or {@code classpath:...}.
     */
    private String configPath = "classpath:demo-config.json";

    /** Whether the check scheduler starts automatically on boot. */
    private boolean autoStart = true;

    private final Log log = new Log();

    private final Cors cors = new Cors();

    public String getConfigPath() {
        return configPath;
    }

    public void setConfigPath(String configPath) {
        this.configPath = configPath;
    }

    public boolean isAutoStart() {
        return autoStart;
    }

    public void setAutoStart(boolean autoStart) {
        this.autoStart = autoStart;
    }

    public Log getLog() {
        return log;
    }

    public Cors getCors() {
        return cors;
    }

    /** CORS settings for the SPA front-end. */
    public static class Cors {
        /**
         * Allowed origin patterns for {@code /api/**}. Defaults to any origin,
         * which is convenient for local development; tighten for production.
         */
        private java.util.List<String> allowedOrigins = new java.util.ArrayList<>(java.util.List.of("*"));

        public java.util.List<String> getAllowedOrigins() {
            return allowedOrigins;
        }

        public void setAllowedOrigins(java.util.List<String> allowedOrigins) {
            this.allowedOrigins = allowedOrigins;
        }
    }

    /** Central-log settings. */
    public static class Log {
        /** Maximum number of entries retained in the in-memory ring buffer. */
        private int capacity = 5000;

        public int getCapacity() {
            return capacity;
        }

        public void setCapacity(int capacity) {
            this.capacity = capacity;
        }
    }
}
