package rsp.compositions;

import rsp.component.ComponentStateSupplier;
import rsp.component.ComponentView;
import rsp.component.definitions.Component;
import rsp.dsl.Definition;

import static rsp.dsl.Html.*;
import static rsp.dsl.Html.body;

/**
 * UiManagementComponent - Resolves ViewContract classes to UI implementations.
 * <p>
 * This component:
 * 1. Reads uiRegistry and route.contractClass from context
 * 2. Finds the appropriate UI component class for the contract
 * 3. Instantiates the UI component
 * 4. Passes it to LayoutComponent for rendering (with optional overlay)
 * <p>
 * Overlay support:
 * When OVERLAY_CONTRACT is present in context, the corresponding UI component
 * is resolved and passed to LayoutComponent as the overlay content.
 * <p>
 * This is pure framework code - no application-specific dependencies.
 */
public class UiManagementComponent extends Component<UiManagementComponent.UiManagementComponentState> {

    /**
     * Default constructor - reads everything from ComponentContext.
     */
    public UiManagementComponent() {
        super();
    }

    @Override
    public ComponentStateSupplier<UiManagementComponentState> initStateSupplier() {
        return (_, context) -> {
            // Read from context and store in state
            UiRegistry uiRegistry = context.get(ContextKeys.UI_REGISTRY);
            Class<? extends ViewContract> contractClass = context.get(ContextKeys.ROUTE_CONTRACT_CLASS);
            Class<? extends ViewContract> overlayContractClass = context.get(ContextKeys.OVERLAY_CONTRACT);
            Class<? extends ViewContract> modalOverlayContractClass = context.get(ContextKeys.MODAL_OVERLAY_CONTRACT);

            return new UiManagementComponentState(uiRegistry, contractClass, overlayContractClass, modalOverlayContractClass);
        };
    }

    @Override
    public ComponentView<UiManagementComponentState> componentView() {
        return _ -> state -> {
            // Read from state
            UiRegistry uiRegistry = state.uiRegistry();
            Class<? extends ViewContract> contractClass = state.contractClass();
            Class<? extends ViewContract> overlayContractClass = state.overlayContractClass();
            Class<? extends ViewContract> modalOverlayContractClass = state.modalOverlayContractClass();

            if (uiRegistry == null || contractClass == null) {
                throw new IllegalStateException("UiRegistry or contractClass not found in state");
            }

            // Resolve primary contract class to UI component
            Component<?> primaryComponent = resolveUiComponent(uiRegistry, contractClass);

            // Resolve overlay contract if present (QUERY_PARAM mode)
            Component<?> overlayComponent = null;
            if (overlayContractClass != null) {
                overlayComponent = resolveUiComponent(uiRegistry, overlayContractClass);
            }

            // Resolve modal overlay contract if present (MODAL mode)
            Component<?> modalOverlayComponent = null;
            if (modalOverlayContractClass != null) {
                modalOverlayComponent = resolveUiComponent(uiRegistry, modalOverlayContractClass);
            }

            // Pass to LayoutComponent with optional overlays
            return page(primaryComponent, overlayComponent, modalOverlayComponent);
        };
    }

    private static Definition page(Component<?> primaryComponent, Component<?> overlayComponent,
                                    Component<?> modalOverlayComponent) {
        return html(head(title("Posts")),
//                        link(attr("rel", "stylesheet"),
//                                attr("href", "/res/style.css"))),
                body(new LayoutComponent(primaryComponent, overlayComponent, modalOverlayComponent)));
    }

    /**
     * Resolves a ViewContract class to its UI implementation.
     * Walks up the class hierarchy to find a registered UI component.
     */
    private Component<?> resolveUiComponent(UiRegistry uiRegistry,
                                           Class<? extends ViewContract> contractClass) {
        // Try the contract class itself first
        Component<?> uiComponent = uiRegistry.resolve(contractClass);
        if (uiComponent != null) {
            return uiComponent;
        }

        // Walk up the inheritance hierarchy to find a registered base class
        Class<?> current = contractClass.getSuperclass();
        while (current != null && ViewContract.class.isAssignableFrom(current)) {
            @SuppressWarnings("unchecked")
            Class<? extends ViewContract> baseClass = (Class<? extends ViewContract>) current;
            uiComponent = uiRegistry.resolve(baseClass);
            if (uiComponent != null) {
                return uiComponent;
            }
            current = current.getSuperclass();
        }

        throw new IllegalStateException("No UI component registered for contract: " + contractClass.getName());
    }

    /**
     * State containing registry and contract classes for UI resolution.
     *
     * @param uiRegistry The registry mapping contracts to UI components
     * @param contractClass The primary contract class for the route
     * @param overlayContractClass The overlay contract class for QUERY_PARAM mode (null if no overlay)
     * @param modalOverlayContractClass The modal overlay contract class for MODAL mode (null if no modal)
     */
    public record UiManagementComponentState(UiRegistry uiRegistry,
                                             Class<? extends ViewContract> contractClass,
                                             Class<? extends ViewContract> overlayContractClass,
                                             Class<? extends ViewContract> modalOverlayContractClass) {
    }
}
