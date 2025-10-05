package rsp.component;

import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderContextFactory;
import rsp.server.RemoteOut;
import rsp.server.http.RelativeUrl;

import java.util.concurrent.CompletableFuture;
import java.util.function.BiFunction;
import java.util.function.Function;
import java.util.function.Supplier;

public abstract class RelativeUrlStateComponentDefinition<S> extends StatefulComponentDefinition<S> {

    private final RelativeUrl relativeUrl;

    protected RelativeUrlStateComponentDefinition(final Object componentType,
                                                  final RelativeUrl relativeUrl) {
        super(componentType);
        this.relativeUrl = relativeUrl;
    }

    protected abstract BiFunction<S, RelativeUrl, RelativeUrl> stateToRelativeUrl();

    protected abstract Function<RelativeUrl, CompletableFuture<? extends S>> relativeUrlToState();

    @Override
    public Component<S> createComponent(QualifiedSessionId sessionId,
                                        TreePositionPath componentPath,
                                        RenderContextFactory renderContextFactory,
                                        RemoteOut remotePageMessagesOut,
                                        Object sessionLock) {
        final ComponentCompositeKey key = new ComponentCompositeKey(sessionId, componentType, componentPath);
        final Supplier<CompletableFuture<? extends S>> resolveStateSupplier = () -> stateSupplier().getState(key);
        return new RelativeUrlStateComponent<>(key,
                                               relativeUrl,
                                               resolveStateSupplier,
                                               componentView(),
                                               new ComponentCallbacks<>(componentDidMount(),
                                                                        componentDidUpdate(),
                                                                        componentWillUnmount()),
                                               renderContextFactory,
                                               remotePageMessagesOut,
                                               stateToRelativeUrl(),
                                               relativeUrlToState(),
                                               sessionLock);
    }
}
