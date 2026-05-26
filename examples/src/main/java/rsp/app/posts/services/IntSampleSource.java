package rsp.app.posts.services;

public interface IntSampleSource {

    int next();

    default int initialWindowSize(final int maxWindowSize) {
        return maxWindowSize;
    }
}
