package ai.luumo.fractalstatus.model.runtime;

import ai.luumo.fractalstatus.model.State;

/**
 * Ephemeral runtime state for a group: its rolled-up {@link State}.
 */
public class GroupRuntime {

    private volatile State state = State.UNKNOWN;

    public State getState() {
        return state;
    }

    public void setState(State state) {
        this.state = state;
    }
}
