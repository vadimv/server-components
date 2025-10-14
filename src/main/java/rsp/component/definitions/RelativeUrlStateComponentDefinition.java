package rsp.component.definitions;

import rsp.component.Component;
import rsp.component.ComponentCallbacks;
import rsp.component.ComponentCompositeKey;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.page.RenderContextFactory;
import rsp.page.events.SessionEvent;
import rsp.server.http.RelativeUrl;

import java.util.Map;
import java.util.function.BiFunction;
import java.util.function.Consumer;
import java.util.function.Function;

public abstract class RelativeUrlStateComponentDefinition<S> extends StatefulComponentDefinition<S> {

    private final RelativeUrl relativeUrl;

    protected RelativeUrlStateComponentDefinition(final Object componentType,
                                                  final RelativeUrl relativeUrl) {
        super(componentType);
        this.relativeUrl = relativeUrl;
    }

    protected abstract BiFunction<S, RelativeUrl, RelativeUrl> stateToRelativeUrl();

    protected abstract Function<RelativeUrl, S> relativeUrlToState();

    @Override
    public Component<S> createComponent(QualifiedSessionId sessionId,
                                        TreePositionPath componentPath,
                                        RenderContextFactory renderContextFactory,
                                        Map<String, Object> sessionObjects,
                                        Consumer<SessionEvent> commandsEnqueue) {
        final ComponentCompositeKey key = new ComponentCompositeKey(sessionId, componentType, componentPath);
        return new RelativeUrlStateComponent<>(key,
                                               relativeUrl,
                                               stateSupplier(),
                                               componentView(),
                                               new ComponentCallbacks<>(onComponentMountedCallback(),
                                                                        onComponentUpdatedCallback(),
                                                                        onComponentUnmountedCallback()),
                                               renderContextFactory,
                                               sessionObjects,
                                               commandsEnqueue,
                                               stateToRelativeUrl(),
                                               relativeUrlToState());
    }
}
