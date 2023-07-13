package rsp.page;

import rsp.component.NewState;

/**
 * The listener interface for receiving a page's session lifecycle events.
 * @param <S> page's root state type, an immutable class
 */
public interface PageLifeCycle<S> {

    /**
     * Invoked after a live page session created.
     * @param sid the qualified session Id of the page created
     * @param rootComponentState the root component's initial state
     * @param rootComponentNewState the root component's state update interface
     */
    void pageCreated(QualifiedSessionId sid, S rootComponentState, NewState<S> rootComponentNewState);

    /**
     * Invoked after an live page session closed.
     * @param sid the qualified session Id of the page being closed
     */
    void pageClosed(QualifiedSessionId sid);

    /**
     * The default lifecycle listener implementation doing nothing.
     * @param <S> page state type, an immutable class
     */
    class Default<S> implements PageLifeCycle<S> {

        @Override
        public void pageCreated(final QualifiedSessionId sid, final S rootComponentState, NewState<S> rootComponentNewState) {
            //no-op
        }

        @Override
        public void pageClosed(final QualifiedSessionId sid) {
            //no-op
        }
    }
}
