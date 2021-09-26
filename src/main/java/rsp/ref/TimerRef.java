package rsp.ref;

import rsp.html.TimerRefDefinition;

public interface TimerRef extends Ref {

    /**
     * Creates a reference to a schedule's timer.
     * @return a reference object
     */
    static TimerRef createTimerRef() {
        return new TimerRefDefinition();
    }
}
