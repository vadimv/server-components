package rsp.compositions.contract;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.compositions.auth.AuthorizationException;
import rsp.compositions.layout.LayoutComponent;
import rsp.compositions.composition.UiRegistry;
import rsp.dsl.Definition;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import static rsp.dsl.Html.*;

/**
 * SceneComponent - Orchestrates Scene building, event handling, context enrichment,
 * and UI rendering.
 * <p>
 * Delegates to:
 * <ul>
 *   <li>{@link SceneBuilder} - Scene construction from composition</li>
 *   <li>{@link SceneEventHandler} - SHOW/HIDE/SET_PRIMARY/ACTION_SUCCESS handlers</li>
 *   <li>{@link SceneContextEnricher} - Context enrichment for downstream components</li>
 *   <li>{@link UiComponentResolver} - ViewContract to UI component resolution</li>
 * </ul>
 * <p>
 * Position in component chain: RoutingComponent → SceneComponent → LayoutComponent
 */
public class SceneComponent extends Component<Scene> {

    private final SceneBuilder sceneBuilder;
    private final SceneContextEnricher contextEnricher;

    private ComponentContext savedContext;

    public SceneComponent(rsp.compositions.composition.Composition composition,
                          Class<? extends ViewContract> contractClass,
                          String routePattern) {
        super();
        Objects.requireNonNull(composition, "composition");
        Objects.requireNonNull(contractClass, "contractClass");
        Objects.requireNonNull(routePattern, "routePattern");
        this.sceneBuilder = new SceneBuilder(composition, contractClass, routePattern);
        this.contextEnricher = new SceneContextEnricher(routePattern);
    }

    /**
     * Build the Scene at component mount time.
     * Primary contract is instantiated, factories for non-primary slots are stored for lazy instantiation.
     */
    @Override
    public ComponentStateSupplier<Scene> initStateSupplier() {
        return (_, context) -> {
            this.savedContext = context;
            return sceneBuilder.buildScene(context);
        };
    }

    /**
     * Enrich context with scene data for downstream components.
     */
    @Override
    public java.util.function.BiFunction<ComponentContext, Scene, ComponentContext> subComponentsContext() {
        return contextEnricher::enrich;
    }

    /**
     * Register SHOW, HIDE, SET_PRIMARY, and ACTION_SUCCESS event handlers.
     */
    @Override
    public void onAfterRendered(Scene state,
                                Subscriber subscriber,
                                CommandsEnqueue commandsEnqueue,
                                StateUpdate<Scene> stateUpdate) {
        SceneEventHandler eventHandler = new SceneEventHandler(savedContext, sceneBuilder);
        eventHandler.registerHandlers(state, subscriber, commandsEnqueue, stateUpdate);
    }

    @Override
    public ComponentView<Scene> componentView() {
        return _ -> scene -> {
            if (scene.error() != null) {
                throw new IllegalStateException("Scene build failed", scene.error());
            }

            if (!scene.authorized()) {
                throw new AuthorizationException("Access denied: insufficient permissions");
            }

            // Get UiRegistry from scene state
            UiRegistry uiRegistry = scene.uiRegistry();
            if (uiRegistry == null) {
                throw new IllegalStateException("UiRegistry not found in scene");
            }

            // Resolve primary contract class to UI component
            Class<? extends ViewContract> contractClass = scene.primaryContract().getClass();
            Component<?> primaryComponent = UiComponentResolver.resolve(uiRegistry, contractClass);

            // Resolve LEFT_SIDEBAR contract to UI component (if present)
            Component<?> leftSidebarComponent = null;
            ViewContract leftSidebarContract = scene.leftSidebarContract();
            if (leftSidebarContract != null) {
                leftSidebarComponent = UiComponentResolver.resolve(uiRegistry, leftSidebarContract.getClass());
            }

            // Resolve active overlay contracts to UI components
            Map<Class<? extends ViewContract>, Component<?>> overlayComponents = new HashMap<>();
            for (Class<? extends ViewContract> overlayClass : scene.nonPrimaryContracts().keySet()) {
                Component<?> overlayComponent = UiComponentResolver.resolve(uiRegistry, overlayClass);
                overlayComponents.put(overlayClass, overlayComponent);
            }

            // Render page with LayoutComponent, passing auto-open info from scene
            return page(primaryComponent,
                        leftSidebarComponent,
                        overlayComponents,
                        scene.autoOpenContract(),
                        scene.autoOpenRoutePattern(),
                        scene.pageTitle());
        };
    }

    /**
     * Renders the page structure with html, head, and body.
     */
    private static Definition page(Component<?> primaryComponent,
                                   Component<?> leftSidebarComponent,
                                   Map<Class<? extends ViewContract>, Component<?>> overlayComponents,
                                   Class<? extends ViewContract> autoOpenContract,
                                   String autoOpenRoutePattern,
                                   String pageTitle) {
        Component<?> layoutComponent = new LayoutComponent(primaryComponent,
                                                           leftSidebarComponent,
                                                           overlayComponents,
                                                           autoOpenContract,
                                                           autoOpenRoutePattern);

        return html(head(title(pageTitle != null ? pageTitle : "App"),
                        link(attr("rel", "stylesheet"),
                             attr("href", "/res/style.css"))),
                body(layoutComponent));
    }
}
