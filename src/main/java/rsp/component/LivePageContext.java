package rsp.component;

import rsp.page.LivePage;

import java.util.Objects;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class LivePageContext implements Supplier<LivePage>, Consumer<LivePage> {
    private volatile LivePage livePage;

    @Override
    public LivePage get() {
        return Objects.requireNonNull(livePage);
    }

    @Override
    public void accept(final LivePage livePage) {
        this.livePage = Objects.requireNonNull(livePage);
    }
}
