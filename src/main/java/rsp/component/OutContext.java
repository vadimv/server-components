package rsp.component;

import rsp.server.OutMessages;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class OutContext implements Supplier<OutMessages>, Consumer<OutMessages> {
    private volatile OutMessages out;

    @Override
    public OutMessages get() {
        return Objects.requireNonNull(out);
    }

    @Override
    public void accept(final OutMessages outMessages) {
        this.out = Objects.requireNonNull(outMessages);
    }
}
