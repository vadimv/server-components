package rsp.compositions.contract;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.compositions.application.ServicesLifecycleHandler;
import rsp.compositions.application.Services;
import rsp.compositions.composition.Composition;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.layout.LayerLayout;
import rsp.compositions.layout.Layout;
import rsp.compositions.layout.ModalLayerLayout;

import java.util.Objects;
import java.util.function.BiFunction;

import static rsp.dsl.Html.*;

/**
 * SceneComponent — the base layer (Layer 0) orchestrating scene building,
 * event handling, context enrichment, and UI rendering.
 * <p>
 * Delegates to:
 * <ul>
 *   <li>{@link SceneBuilder} - Scene construction from composition</li>
 *   <li>{@link SceneEventHandler} - SET_PRIMARY handler for base layer</li>
 *   <li>{@link SceneContextEnricher} - Context enrichment for downstream components</li>
 *   <li>{@link Layout} - Base layer visual arrangement (also declares required companions)</li>
 *   <li>{@link LayerComponent} - Upper layers (overlays, panels) with pluggable {@link LayerLayout}</li>
 * </ul>
 * <p>
 * Position in component chain: AuthComponent → SceneComponent → [Layout, LayerComponent]
 */
public class SceneComponent extends Component<Scene> {
    private final System.Logger logger = System.getLogger(getClass().getName());

    private final SceneBuilder sceneBuilder;
    private final SceneContextEnricher contextEnricher;
    private final Layout layout;
    private final LayerLayout layerLayout;

    private ComponentContext savedContext;


    public SceneComponent(Object componentType,
                          Composition composition,
                          Class<? extends ViewContract> contractClass,
                          String routePattern) {
        this(componentType, composition, contractClass, routePattern, new DefaultLayout(), new ModalLayerLayout());
    }

    public SceneComponent(Object componentType,
                          Composition composition,
                          Class<? extends ViewContract> contractClass,
                          String routePattern,
                          Layout layout) {
        this(componentType, composition, contractClass, routePattern, layout, new ModalLayerLayout());
    }

    public SceneComponent(Object componentType,
                          Composition composition,
                          Class<? extends ViewContract> contractClass,
                          String routePattern,
                          Layout layout,
                          LayerLayout layerLayout) {
        super(componentType);
        Objects.requireNonNull(composition, "composition");
        Objects.requireNonNull(contractClass, "contractClass");
        Objects.requireNonNull(routePattern, "routePattern");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.sceneBuilder = new SceneBuilder(composition, contractClass, routePattern, layout);
        this.contextEnricher = new SceneContextEnricher(routePattern);
        this.layerLayout = Objects.requireNonNull(layerLayout, "layerLayout");
    }

    @Override
    public ComponentStateSupplier<Scene> initStateSupplier() {
        return (_, context) -> {
            this.savedContext = context;
            return sceneBuilder.buildScene(context);
        };
    }

    @Override
    public BiFunction<ComponentContext, Scene, ComponentContext> subComponentsContext() {
        return contextEnricher::enrich;
    }

    @Override
    public void onAfterRendered(Scene state,
                                Subscriber subscriber,
                                CommandsEnqueue commandsEnqueue,
                                StateUpdate<Scene> stateUpdate) {
        SceneEventHandler eventHandler = new SceneEventHandler(savedContext);
        eventHandler.registerHandlers(state, subscriber, commandsEnqueue, stateUpdate);
    }

    @Override
    public void onUnmounted(ComponentCompositeKey componentId, Scene scene) {
        if (scene == null) {
            return;
        }
        // Destroy routed contract
        if (scene.routedContract() != null) {
            scene.routedContract().onDestroy();
        }
        // Destroy all companion contracts
        for (var companion : scene.companionContracts().values()) {
            companion.onDestroy();
        }
        stopServicesLifecycleHandlers(scene);
    }

    private void stopServicesLifecycleHandlers(Scene scene) {
        Composition composition = scene.composition();
        if (composition == null) return;
        Services services = composition.services();
        if (services == null) return;
        for (Object service : services.asMap().values()) {
            if (service instanceof ServicesLifecycleHandler handler) {
                handler.onStop();
            }
        }
    }

    @Override
    public ComponentView<Scene> componentView() {
        return _ -> scene ->
            html(head(title(scene.pageTitle()),
                            link(attr("rel", "stylesheet"),
                                 attr("href", "/res/style.css"))),
                    body(layout.resolve(scene, LookupFactory.create(savedContext)),
                         new LayerComponent(layerLayout)));
    }
}
