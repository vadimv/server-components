package rsp.compositions;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.dom.TreePositionPath;
import rsp.page.QualifiedSessionId;
import rsp.page.events.Command;
import rsp.page.events.ComponentEventNotification;

import java.util.Map;
import java.util.function.Consumer;

import static rsp.compositions.EventKeys.*;
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
 *   <li>QUERY_PARAM mode: ?create=true in URL triggers overlay (set at construction)</li>
 *   <li>MODAL mode: openCreateModal event triggers overlay dynamically</li>
 * </ul>
 * <p>
 * Listens for events:
 * <ul>
 *   <li>"openCreateModal" - Opens create modal (MODAL mode)</li>
 *   <li>"closeOverlay" - Closes the overlay</li>
 *   <li>"modalSaveSuccess" - Closes modal and triggers list refresh</li>
 *   <li>"modalDeleteSuccess" - Closes modal and triggers list refresh</li>
 * </ul>
 */
public class LayoutComponent extends Component<LayoutComponent.LayoutComponentState> {

    private final Component<?> primaryComponent;
    private final Component<?> overlayComponent;
    private final Component<?> modalOverlayComponent;

    private CommandsEnqueue commandsEnqueue;

    /**
     * Creates a LayoutComponent with primary content only.
     *
     * @param primaryComponent The main UI component to render
     */
    public LayoutComponent(Component<?> primaryComponent) {
        this(primaryComponent, null, null);
    }

    /**
     * Creates a LayoutComponent with primary and optional overlay content.
     *
     * @param primaryComponent The main UI component to render
     * @param overlayComponent The overlay component to render (null = no overlay)
     */
    public LayoutComponent(Component<?> primaryComponent, Component<?> overlayComponent) {
        this(primaryComponent, overlayComponent, null);
    }

    /**
     * Creates a LayoutComponent with primary, overlay, and modal overlay content.
     *
     * @param primaryComponent The main UI component to render
     * @param overlayComponent The overlay component for QUERY_PARAM mode (null = no overlay)
     * @param modalOverlayComponent The overlay component for MODAL mode (dynamically shown)
     */
    public LayoutComponent(Component<?> primaryComponent, Component<?> overlayComponent, Component<?> modalOverlayComponent) {
        super();
        this.primaryComponent = primaryComponent;
        this.overlayComponent = overlayComponent;
        this.modalOverlayComponent = modalOverlayComponent;
    }

    @Override
    public ComponentStateSupplier<LayoutComponentState> initStateSupplier() {
        return (_, _) -> new LayoutComponentState(primaryComponent, overlayComponent, modalOverlayComponent, false);
    }

    @Override
    public ComponentSegment<LayoutComponentState> createComponentSegment(final QualifiedSessionId sessionId,
                                                                          final TreePositionPath componentPath,
                                                                          final TreeBuilderFactory treeBuilderFactory,
                                                                          final ComponentContext componentContext,
                                                                          final CommandsEnqueue commandsEnqueue) {
        this.commandsEnqueue = commandsEnqueue;
        return super.createComponentSegment(sessionId, componentPath, treeBuilderFactory, componentContext, commandsEnqueue);
    }

    @Override
    public void onAfterRendered(LayoutComponentState state,
                                Subscriber subscriber,
                                CommandsEnqueue commandsEnqueue,
                                StateUpdate<LayoutComponentState> stateUpdate) {
        // Register handler for openCreateModal event (MODAL mode)
        subscriber.addEventHandler(OPEN_CREATE_MODAL, () -> {
            // Show the modal overlay
            stateUpdate.applyStateTransformation(s -> s.withModalOpen(true));
        }, false);

        // Register handler for closeOverlay event
        subscriber.addEventHandler(CLOSE_OVERLAY, () -> {
            // For MODAL mode, close without URL navigation
            stateUpdate.applyStateTransformation(s -> s.withModalOpen(false));
            // For QUERY_PARAM mode, emit event to parent for URL update
            commandsEnqueue.offer(new ComponentEventNotification("overlayCloseRequested", Map.of()));
        }, false);

        // Register handler for modalSaveSuccess event (close modal + refresh list)
        subscriber.addEventHandler(MODAL_SAVE_SUCCESS, () -> {
            // Close the modal
            stateUpdate.applyStateTransformation(s -> s.withModalOpen(false));
            // Trigger list refresh
            commandsEnqueue.offer(new ComponentEventNotification("refreshList", Map.of()));
        }, false);

        // Register handler for modalDeleteSuccess event (close modal + refresh list)
        subscriber.addEventHandler(MODAL_DELETE_SUCCESS, () -> {
            // Close the modal
            stateUpdate.applyStateTransformation(s -> s.withModalOpen(false));
            // Trigger list refresh
            commandsEnqueue.offer(new ComponentEventNotification("refreshList", Map.of()));
        }, false);
    }

    @Override
    public ComponentView<LayoutComponentState> componentView() {
        return _ -> state -> {
            Component<?> primary = state.primaryComponent();
            Component<?> overlay = state.overlayComponent();
            Component<?> modalOverlay = state.modalOverlayComponent();
            boolean modalOpen = state.isModalOpen();

            // Determine which overlay to show
            Component<?> activeOverlay = null;
            if (overlay != null) {
                // QUERY_PARAM mode: overlay set at construction
                activeOverlay = overlay;
            } else if (modalOpen && modalOverlay != null) {
                // MODAL mode: modal dynamically opened
                activeOverlay = modalOverlay;
            }

            if (activeOverlay == null) {
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
                    renderOverlay(activeOverlay)
            );
        };
    }

    /**
     * Renders the overlay with backdrop and content.
     */
    private rsp.dsl.Definition renderOverlay(Component<?> content) {
        return div(attr("class", "modal-overlay"),
                // Semi-transparent backdrop - closes overlay on click
                div(attr("class", "modal-backdrop"),
                        on("click", ctx -> {
                            // Send closeOverlay event to trigger state change
                            commandsEnqueue.offer(new ComponentEventNotification("closeOverlay", Map.of()));
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
     * @param overlayComponent The overlay component for QUERY_PARAM mode (null = no overlay shown)
     * @param modalOverlayComponent The overlay component for MODAL mode
     * @param isModalOpen Whether the modal is currently open (MODAL mode only)
     */
    public record LayoutComponentState(
            Component<?> primaryComponent,
            Component<?> overlayComponent,
            Component<?> modalOverlayComponent,
            boolean isModalOpen
    ) {
        /**
         * Backwards-compatible constructor without modal support.
         */
        public LayoutComponentState(Component<?> primaryComponent, Component<?> overlayComponent) {
            this(primaryComponent, overlayComponent, null, false);
        }

        /**
         * Creates a new state with overlay component.
         *
         * @param overlay The overlay component to show
         * @return New state with overlay
         */
        public LayoutComponentState withOverlay(Component<?> overlay) {
            return new LayoutComponentState(primaryComponent, overlay, modalOverlayComponent, isModalOpen);
        }

        /**
         * Creates a new state without overlay.
         *
         * @return New state with no overlay
         */
        public LayoutComponentState withoutOverlay() {
            return new LayoutComponentState(primaryComponent, null, modalOverlayComponent, isModalOpen);
        }

        /**
         * Creates a new state with modal open/closed.
         *
         * @param open Whether modal should be open
         * @return New state with modal open/closed
         */
        public LayoutComponentState withModalOpen(boolean open) {
            return new LayoutComponentState(primaryComponent, overlayComponent, modalOverlayComponent, open);
        }

        /**
         * Check if any overlay is currently shown.
         *
         * @return true if overlay is active
         */
        public boolean hasOverlay() {
            return overlayComponent != null || (isModalOpen && modalOverlayComponent != null);
        }
    }
}
