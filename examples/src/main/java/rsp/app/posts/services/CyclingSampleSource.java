package rsp.app.posts.services;

import java.util.List;

public final class CyclingSampleSource implements IntSampleSource {

    private final List<Integer> values;
    private int index;

    public CyclingSampleSource(final List<Integer> values) {
        List<Integer> safe = values == null ? List.of() : List.copyOf(values);
        if (safe.isEmpty()) {
            throw new IllegalArgumentException("values must contain at least one element");
        }
        this.values = safe;
    }

    @Override
    public int next() {
        int value = values.get(index);
        index = (index + 1) % values.size();
        return value;
    }

    @Override
    public int initialWindowSize(final int maxWindowSize) {
        return Math.min(values.size(), maxWindowSize);
    }
}
