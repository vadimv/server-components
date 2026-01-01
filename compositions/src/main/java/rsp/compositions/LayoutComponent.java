package rsp.compositions;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;

/**
 * LayoutComponent - Renders UI components in layout slots.
 * <p>
 * For Phase 1, this simply renders the UI component directly.
 * Future phases will implement slot-based layout (PRIMARY, SECONDARY, OVERLAY).
 */
public class LayoutComponent extends Component<LayoutComponent.LayoutComponentState> {

    private final Component<?> uiComponent;

    /**
     * Accepts the resolved UI component to render.
     *
     * @param uiComponent The UI component (e.g., DefaultListView)
     */
    public LayoutComponent(Component<?> uiComponent) {
        super();
        this.uiComponent = uiComponent;
    }

    @Override
    public ComponentStateSupplier<LayoutComponentState> initStateSupplier() {
        return (_, _) -> new LayoutComponentState();
    }

    @Override
    public ComponentView<LayoutComponentState> componentView() {
        // Phase 1: Just render the UI component directly
        // Phase 2: Will implement slot-based layout
        return _ -> _ -> uiComponent;
    }

    public record LayoutComponentState() {
    }
}
