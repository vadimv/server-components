package rsp.compositions;

import rsp.component.ComponentContext;
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
 * 4. Passes it to LayoutComponent for rendering
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
            UiRegistry uiRegistry = (UiRegistry) context.getAttribute("app.uiRegistry");
            @SuppressWarnings("unchecked")
            Class<? extends ViewContract> contractClass =
                (Class<? extends ViewContract>) context.getAttribute("route.contractClass");

            return new UiManagementComponentState(uiRegistry, contractClass);
        };
    }

    @Override
    public ComponentView<UiManagementComponentState> componentView() {
        return _ -> state -> {
            // Read from state
            UiRegistry uiRegistry = state.uiRegistry();
            Class<? extends ViewContract> contractClass = state.contractClass();

            if (uiRegistry == null || contractClass == null) {
                throw new IllegalStateException("UiRegistry or contractClass not found in state");
            }

            // Resolve contract class to UI component
            Component<?> uiComponent = resolveUiComponent(uiRegistry, contractClass);

            // Pass to LayoutComponent
            return page(uiComponent);
        };
    }

    private static Definition page(Component<?> uiComponent) {
        return html(head(title("Posts")),
//                        link(attr("rel", "stylesheet"),
//                                attr("href", "/res/style.css"))),
                body(new LayoutComponent(uiComponent)));
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

    public record UiManagementComponentState(UiRegistry uiRegistry,
                                             Class<? extends ViewContract> contractClass) {
    }
}
