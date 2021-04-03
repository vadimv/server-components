package rsp.page;

/**
 * The listener interface for receiving page lifecycle events.
 * @param <S>
 */
public interface PageLifeCycle<S> {

    /**
     * Invoked before an live page session created.
     * @param state
     */
    void beforeLivePageCreated(S state);

    /**
     * Invoked after an live page session closed.
     * @param state
     */
    void afterLivePageClosed(S state);

    /**
     * The default lifecycle listener implementation doing nothing.
     * @param <S>
     */
    class Default<S> implements PageLifeCycle<S> {

        @Override
        public void beforeLivePageCreated(S state) {
            //no-op
        }

        @Override
        public void afterLivePageClosed(S state) {
            //no-op
        }
    }
}
