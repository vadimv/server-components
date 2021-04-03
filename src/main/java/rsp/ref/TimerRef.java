package rsp.ref;

import rsp.dsl.TimerRefDefinition;
import rsp.page.EventContext;

import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

public interface TimerRef extends Ref {

    /**
     * Creates a reference to a schedule's timer.
     * @see EventContext#schedule(Consumer, TimerRef, int, TimeUnit)
     * @see EventContext#scheduleAtFixedRate(Consumer, int, int, TimeUnit)
     * @return a reference object
     */
    static TimerRef createTimerRef() {
        return new TimerRefDefinition();
    }
}
