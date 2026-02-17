package rsp.app.posts.components;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;

import static rsp.dsl.Html.*;

/**
 * HeaderView - Renders a horizontal stripe showing the active category name.
 * <p>
 * Reads the active category from context (set by {@link HeaderContract#enrichContext}).
 */
public class HeaderView extends Component<HeaderView.HeaderViewState> {

    public record HeaderViewState(String activeCategory) {}

    @Override
    public ComponentStateSupplier<HeaderViewState> initStateSupplier() {
        return (_, context) -> {
            String category = context.get(HeaderContract.HEADER_CATEGORY);
            return new HeaderViewState(category != null ? category : "");
        };
    }

    @Override
    public ComponentView<HeaderViewState> componentView() {
        return _ -> state -> div(attr("class", "layout-header"),
                span(attr("class", "header-category"), text(state.activeCategory()))
        );
    }
}
