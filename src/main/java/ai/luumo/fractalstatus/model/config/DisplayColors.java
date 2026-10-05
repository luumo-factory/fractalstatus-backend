package ai.luumo.fractalstatus.model.config;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Optional per-state colour overrides for a node tile (hex strings, e.g.
 * {@code "#2E7D32"}). Any field left null falls back to the front-end's default
 * palette.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public class DisplayColors {

    private String online;
    private String warning;
    private String offline;
    private String unknown;

    public String getOnline() {
        return online;
    }

    public void setOnline(String online) {
        this.online = online;
    }

    public String getWarning() {
        return warning;
    }

    public void setWarning(String warning) {
        this.warning = warning;
    }

    public String getOffline() {
        return offline;
    }

    public void setOffline(String offline) {
        this.offline = offline;
    }

    public String getUnknown() {
        return unknown;
    }

    public void setUnknown(String unknown) {
        this.unknown = unknown;
    }
}
