package rsp.component;

import rsp.server.Out;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class OutContext implements Supplier<Out>, Consumer<Out> {
    private volatile Out out;

    @Override
    public Out get() {
        return Objects.requireNonNull(out);
    }

    @Override
    public void accept(final Out out) {
        this.out = Objects.requireNonNull(out);
    }
}
