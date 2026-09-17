package rsp.compositions.block;

import rsp.component.*;
import rsp.component.definitions.Component;
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
public class SceneComponent extends Component<Scene, Object> {
    private final System.Logger logger = System.getLogger(getClass().getName());

    private final SceneBuilder sceneBuilder;
    private final SceneContextEnricher contextEnricher;
    private final Layout layout;
    private final LayerLayout layerLayout;

    private ContextScope activeContextScope;


    public SceneComponent(Object componentType,
                          Composition composition,
                          Class<? extends Block<?, ?>> blockClass,
                          String routePattern) {
        this(componentType, composition, blockClass, blockClass, routePattern,
                new DefaultLayout(), new ModalLayerLayout());
    }

    public SceneComponent(Object componentType,
                          Composition composition,
                          Class<? extends Block<?, ?>> blockClass,
                          String routePattern,
                          Layout layout) {
        this(componentType, composition, blockClass, blockClass, routePattern, layout, new ModalLayerLayout());
    }

    public SceneComponent(Object componentType,
                          Composition composition,
                          Object blockKey,
                          Class<? extends Block<?, ?>> blockClass,
                          String routePattern,
                          Layout layout,
                          LayerLayout layerLayout) {
        super(componentType);
        Objects.requireNonNull(composition, "composition");
        Objects.requireNonNull(blockClass, "blockClass");
        Objects.requireNonNull(routePattern, "routePattern");
        this.layout = Objects.requireNonNull(layout, "layout");
        this.sceneBuilder = new SceneBuilder(composition,
                new BlockTarget(Objects.requireNonNull(blockKey, "blockKey"), blockClass),
                routePattern, layout);
        this.contextEnricher = new SceneContextEnricher(routePattern);
        this.layerLayout = Objects.requireNonNull(layerLayout, "layerLayout");
    }

    public SceneComponent(Object componentType,
                          Composition composition,
                          Object blockKey,
                          Class<? extends Block<?, ?>> blockClass,
                          String routePattern,
                          Layout layout) {
        this(componentType, composition, blockKey, blockClass, routePattern, layout,
                new ModalLayerLayout());
    }

    public SceneComponent(Object componentType,
                          Composition composition,
                          Class<? extends Block<?, ?>> blockClass,
                          String routePattern,
                          Layout layout,
                          LayerLayout layerLayout) {
        this(componentType, composition, blockClass, blockClass, routePattern, layout, layerLayout);
    }

    @Override
    public ComponentStateSupplier<Scene> initStateSupplier() {
        return (_, context) -> sceneBuilder.buildScene(context);
    }

    @Override
    public BiFunction<ComponentContext, Scene, ComponentContext> subComponentsContext() {
        return contextEnricher::enrich;
    }

    @Override
    public void onBeforeRendered(ComponentSegment<Scene> segment, Scene state) {
        activeContextScope = segment.contextScope();
    }

    @Override
    public void onAfterRendered(Scene state,
                                Subscriber subscriber,
                                CommandsEnqueue commandsEnqueue,
                                StateUpdater<Scene> stateUpdate) {
        SceneEventHandler eventHandler = new SceneEventHandler(activeContext());
        eventHandler.registerHandlers(state, subscriber, commandsEnqueue, stateUpdate);
    }

    @Override
    public void onUnmounted(ComponentCompositeKey componentId, Scene scene) {
        if (scene == null) {
            return;
        }
        activeContextScope = null;
    }

    @Override
    public boolean isReusable() {
        return true;
    }

    @Override
    public ComponentView<Scene, Object> componentView() {
        return _ -> scene ->
            html(head(title(scene.pageTitle()),
                            link(attr("rel", "stylesheet"),
                                 attr("href", "/res/style.css"))),
                    body(layout.resolve(scene, LookupFactory.create(activeContext())),
                         new LayerComponent(layerLayout)));
    }

    private ComponentContext activeContext() {
        if (activeContextScope == null) {
            throw new IllegalStateException("SceneComponent has no live context scope");
        }
        return activeContextScope.current();
    }
}
