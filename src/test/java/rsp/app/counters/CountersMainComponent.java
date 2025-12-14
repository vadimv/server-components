package rsp.app.counters;

import rsp.component.ComponentCompositeKey;
import rsp.component.ComponentView;
import rsp.component.definitions.AddressBarSyncComponent;
import rsp.server.http.RelativeUrl;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static rsp.dsl.Html.br;
import static rsp.dsl.Html.div;

public class CountersMainComponent extends AddressBarSyncComponent {

    private static final Map<ComponentCompositeKey, Integer> stateStore = new ConcurrentHashMap<>();

    public CountersMainComponent(RelativeUrl initialRelativeUrl) {
        super(initialRelativeUrl);
    }

    @Override
    public List<PositionKey> pathElementsPositionKeys() {
        return List.of(new PositionKey(0, "c1"), new PositionKey(1, "c2"));
    }

    @Override
    public List<ParameterNameKey> queryParametersNamedKeys() {
        return List.of(new ParameterNameKey("c4", "c4"));
    }

    @Override
    public ComponentView<RelativeUrl> componentView() {
        return _ ->_ -> div(new ContextCounterComponent("c1"),
                                                        br(),
                                                        new ContextCounterComponent("c2"),
                                                        br(),
                                                        new HideableCounterComponent("c3", stateStore),
                                                        new ContextCounterComponent("c4"));
    }
}
