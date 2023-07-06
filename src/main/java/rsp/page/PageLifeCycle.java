package rsp.page;

/**
 * The listener interface for receiving a page's session lifecycle events.
 * @param <S> page's root state type, an immutable class
 */
public interface PageLifeCycle<S> {

    /**
     * Invoked before an live page session created.
     * @param sid the qualified session Id of the page created
     * @param state the initial state
     *
     */
    void beforePageCreated(QualifiedSessionId sid, S state);

    /**
     * Invoked after an live page session closed.
     * @param sid the qualified session Id of the page being closed
     */
    void afterPageClosed(QualifiedSessionId sid);

    /**
     * The default lifecycle listener implementation doing nothing.
     * @param <S> page state type, an immutable class
     */
    class Default<S> implements PageLifeCycle<S> {

        @Override
        public void beforePageCreated(final QualifiedSessionId sid, final S state) {
            //no-op
        }

        @Override
        public void afterPageClosed(final QualifiedSessionId sid) {
            //no-op
        }
    }
}
