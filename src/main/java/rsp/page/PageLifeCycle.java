package rsp.page;

public interface PageLifeCycle<S> {

    void beforeLivePageCreated(S state);

    void afterLivePageClosed(S state);

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
