package rsp.compositions;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.page.events.Command;
import rsp.page.events.ComponentEventNotification;

import java.util.function.Consumer;

import static rsp.dsl.Html.*;

/**
 * LayoutComponent - Renders UI components in layout slots with overlay support.
 * <p>
 * Provides three rendering slots:
 * <ul>
 *   <li><b>PRIMARY</b> - Main content area, always visible</li>
 *   <li><b>SECONDARY</b> - Optional secondary content (reserved for future use)</li>
 *   <li><b>OVERLAY</b> - Modal/dialog overlay, conditionally rendered</li>
 * </ul>
 * <p>
 * Overlay can be triggered by:
 * <ul>
 *   <li>QUERY_PARAM mode: ?create=true in URL triggers overlay</li>
 *   <li>MODAL mode: openOverlay event triggers overlay</li>
 * </ul>
 * <p>
 * Listens for events:
 * <ul>
 *   <li>"openOverlay" - Opens overlay with provided component</li>
 *   <li>"closeOverlay" - Closes the overlay</li>
 * </ul>
 */
public class LayoutComponent extends Component<LayoutComponent.LayoutComponentState> {

    private final Component<?> primaryComponent;
    private final Component<?> overlayComponent;

    private Consumer<Command> commandsEnqueue;

    /**
     * Creates a LayoutComponent with primary content only.
     *
     * @param primaryComponent The main UI component to render
     */
    public LayoutComponent(Component<?> primaryComponent) {
        this(primaryComponent, null);
    }

    /**
     * Creates a LayoutComponent with primary and optional overlay content.
     *
     * @param primaryComponent The main UI component to render
     * @param overlayComponent The overlay component to render (null = no overlay)
     */
    public LayoutComponent(Component<?> primaryComponent, Component<?> overlayComponent) {
        super();
        this.primaryComponent = primaryComponent;
        this.overlayComponent = overlayComponent;
    }

    @Override
    public ComponentStateSupplier<LayoutComponentState> initStateSupplier() {
        return (_, _) -> new LayoutComponentState(primaryComponent, overlayComponent);
    }

    @Override
    public ComponentSegment<LayoutComponentState> createComponentSegment(final QualifiedSessionId sessionId,
                                                                          final TreePositionPath componentPath,
                                                                          final TreeBuilderFactory treeBuilderFactory,
                                                                          final ComponentContext componentContext,
                                                                          final Consumer<Command> commandsEnqueue) {
        this.commandsEnqueue = commandsEnqueue;

        ComponentSegment<LayoutComponentState> segment = super.createComponentSegment(
                sessionId, componentPath, treeBuilderFactory, componentContext, commandsEnqueue);

        // Register handler for openOverlay event
        segment.addComponentEventHandler("openOverlay", eventContext -> {
            Component<?> newOverlayComponent = (Component<?>) eventContext.eventObject();
            // Note: State update would require access to newState consumer from view
            // For now, overlays are set at construction time via UiManagementComponent
        }, false);

        // Register handler for closeOverlay event
        segment.addComponentEventHandler("closeOverlay", eventContext -> {
            // Emit event to parent (UiManagementComponent) to handle URL/state update
            commandsEnqueue.accept(new ComponentEventNotification("overlayCloseRequested", null));
        }, false);

        return segment;
    }

    @Override
    public ComponentView<LayoutComponentState> componentView() {
        return stateUpdate -> state -> {
            Component<?> primary = state.primaryComponent();
            Component<?> overlay = state.overlayComponent();

            if (overlay == null) {
                // No overlay - just render primary component
                return div(attr("class", "layout-container"),
                        div(attr("class", "layout-primary"),
                                primary
                        )
                );
            }

            // Render with overlay
            return div(attr("class", "layout-container"),
                    // Primary slot - always visible but may be behind overlay
                    div(attr("class", "layout-primary"),
                            primary
                    ),

                    // Overlay slot - modal overlay
                    renderOverlay(overlay, stateUpdate)
            );
        };
    }

    /**
     * Renders the overlay with backdrop and content.
     */
    private rsp.dsl.Definition renderOverlay(Component<?> content,
                                              StateUpdate<LayoutComponentState> stateUpdate) {
        return div(attr("class", "modal-overlay"),
                // Semi-transparent backdrop - closes overlay on click
                div(attr("class", "modal-backdrop"),
                        on("click", ctx -> {
                            // Send closeOverlay event to trigger URL update and state change
                            commandsEnqueue.accept(new ComponentEventNotification("closeOverlay", null));
                        })
                ),
                // Modal content container
                div(attr("class", "modal-content"),
                        content
                )
        );
    }

    /**
     * State for LayoutComponent with multi-slot support.
     *
     * @param primaryComponent The main content component (always present)
     * @param overlayComponent The overlay component (null = no overlay shown)
     */
    public record LayoutComponentState(
            Component<?> primaryComponent,
            Component<?> overlayComponent
    ) {
        /**
         * Creates a new state with overlay component.
         *
         * @param overlay The overlay component to show
         * @return New state with overlay
         */
        public LayoutComponentState withOverlay(Component<?> overlay) {
            return new LayoutComponentState(primaryComponent, overlay);
        }

        /**
         * Creates a new state without overlay.
         *
         * @return New state with no overlay
         */
        public LayoutComponentState withoutOverlay() {
            return new LayoutComponentState(primaryComponent, null);
        }

        /**
         * Check if overlay is currently shown.
         *
         * @return true if overlay is active
         */
        public boolean hasOverlay() {
            return overlayComponent != null;
        }
    }
}
