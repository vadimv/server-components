package rsp.actor;

import java.util.concurrent.TimeoutException;

/** The reply deadline expired; the admitted actor command may still complete. */
public final class ActorAskTimeoutException extends TimeoutException {
    public ActorAskTimeoutException() {
        super("Actor ask timed out");
    }
}
