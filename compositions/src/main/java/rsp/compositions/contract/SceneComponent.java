package rsp.compositions.contract;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.compositions.auth.AuthorizationException;
import rsp.compositions.composition.UiRegistry;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.layout.Layout;
import rsp.dsl.Definition;

import java.util.Objects;

import static rsp.compositions.contract.EventKeys.HIDE;
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
 *   <li>{@link Layout} - Visual arrangement of resolved UI components</li>
 * </ul>
 * <p>
 * Position in component chain: RoutingComponent → SceneComponent → (Layout renders children)
 */
public class SceneComponent extends Component<Scene> {

    private final SceneBuilder sceneBuilder;
    private final SceneContextEnricher contextEnricher;
    private final Layout layout;

    private ComponentContext savedContext;

    public SceneComponent(rsp.compositions.composition.Composition composition,
                          Class<? extends ViewContract> contractClass,
                          String routePattern) {
        this(composition, contractClass, routePattern, new DefaultLayout());
    }

    public SceneComponent(rsp.compositions.composition.Composition composition,
                          Class<? extends ViewContract> contractClass,
                          String routePattern,
                          Layout layout) {
        super();
        Objects.requireNonNull(composition, "composition");
        Objects.requireNonNull(contractClass, "contractClass");
        Objects.requireNonNull(routePattern, "routePattern");
        this.sceneBuilder = new SceneBuilder(composition, contractClass, routePattern);
        this.contextEnricher = new SceneContextEnricher(routePattern);
        this.layout = Objects.requireNonNull(layout, "layout");
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

            UiRegistry uiRegistry = scene.uiRegistry();
            if (uiRegistry == null) {
                throw new IllegalStateException("UiRegistry not found in scene");
            }

            // Resolve primary contract to UI component
            Component<?> primaryComponent = UiComponentResolver.resolve(
                    uiRegistry, scene.primaryContract().getClass());

            // Resolve LEFT_SIDEBAR contract to UI component (if present)
            Component<?> sidebarComponent = null;
            ViewContract sidebarContract = scene.leftSidebarContract();
            if (sidebarContract != null) {
                sidebarComponent = UiComponentResolver.resolve(uiRegistry, sidebarContract.getClass());
            }

            // Determine active overlay and resolve to UI component
            Component<?> activeOverlayComponent = null;
            Class<? extends ViewContract> activeOverlayClass = scene.autoOpenContract();
            if (activeOverlayClass == null && scene.hasNonPrimaryContracts()) {
                activeOverlayClass = scene.nonPrimaryContracts().keySet().iterator().next();
            }
            if (activeOverlayClass != null) {
                activeOverlayComponent = UiComponentResolver.resolve(uiRegistry, activeOverlayClass);
            }

            // Create close handler for the active overlay
            final Class<? extends ViewContract> overlayToClose = activeOverlayClass;
            Runnable onOverlayClose = overlayToClose != null
                    ? () -> LookupFactory.create(savedContext).publish(HIDE, overlayToClose)
                    : null;

            // Render layout
            Definition layoutDef = layout.render(
                    primaryComponent, sidebarComponent, activeOverlayComponent, onOverlayClose);

            return html(head(title(scene.pageTitle() != null ? scene.pageTitle() : "App"),
                            link(attr("rel", "stylesheet"),
                                 attr("href", "/res/style.css"))),
                    body(layoutDef));
        };
    }
}
