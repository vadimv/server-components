package rsp.examples.crud.components;

import rsp.Component;
import rsp.dsl.DocumentPartDefinition;
import rsp.dsl.Html;
import rsp.state.UseState;
import rsp.util.Tuple2;

import java.util.List;

import static rsp.dsl.Html.*;

public class MenuPanel implements Component<MenuPanel.State> {

    @Override
    public DocumentPartDefinition render(UseState<State> state) {
        return div(
                    ul(
                        Html.of(state.get().resourcesInfos.stream().map(r -> li(a("/" + r._1, r._2)))
                    )));
    }

    public static class State {
        public final List<Tuple2<String, String>> resourcesInfos;

        public State(List<Tuple2<String, String>> resourcesInfos) {
            this.resourcesInfos = resourcesInfos;
        }
    }
}
