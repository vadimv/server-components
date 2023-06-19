package rsp.page;

/**
 * The listener interface for receiving page lifecycle events.
 * @param <S> page's root state type, an immutable class
 */
public interface PageLifeCycle<S> {

    /**
     * Invoked before an live page session created.
     * @param sid the qualified session Id of the page created
     * @param useState the accessor object for reading and writing page's state,
     *                 use it as an intrinsic lock in a {@code synchronized} statement
     *                 to ensure atomic read-and-write of state
     *
     */
    void beforeLivePageCreated(QualifiedSessionId sid, S state);

    /**
     * Invoked after an live page session closed.
     * @param sid the qualified session Id of the page being closed
     */
    void afterLivePageClosed(QualifiedSessionId sid);

    /**
     * The default lifecycle listener implementation doing nothing.
     * @param <S> page state type, an immutable class
     */
    class Default<S> implements PageLifeCycle<S> {

        @Override
        public void beforeLivePageCreated(final QualifiedSessionId sid, final S state) {
            //no-op
        }

        @Override
        public void afterLivePageClosed(final QualifiedSessionId sid) {
            //no-op
        }
    }
}
