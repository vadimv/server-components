package rsp.compositions.contract;

import rsp.component.*;
import rsp.component.definitions.Component;
import rsp.compositions.auth.AuthorizationException;
import rsp.compositions.composition.Composition;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.layout.Layout;

import java.util.Objects;
import java.util.function.BiFunction;

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
 *   <li>{@link Layout} - Contract resolution and visual arrangement</li>
 * </ul>
 * <p>
 * Position in component chain: RoutingComponent → SceneComponent → (Layout renders children)
 */
public class SceneComponent extends Component<Scene> {
    private final System.Logger logger = System.getLogger(getClass().getName());

    private final SceneBuilder sceneBuilder;
    private final SceneContextEnricher contextEnricher;
    private final Layout layout;

    private ComponentContext savedContext;


    public SceneComponent(Object componentType,
                          Composition composition,
                          Class<? extends ViewContract> contractClass,
                          String routePattern) {
        this(componentType, composition, contractClass, routePattern, new DefaultLayout());
    }

    public SceneComponent(Object componentType,
                          Composition composition,
                          Class<? extends ViewContract> contractClass,
                          String routePattern,
                          Layout layout) {
        super(componentType);
        Objects.requireNonNull(composition, "composition");
        Objects.requireNonNull(contractClass, "contractClass");
        Objects.requireNonNull(routePattern, "routePattern");
        this.sceneBuilder = new SceneBuilder(composition, contractClass, routePattern);
        this.contextEnricher = new SceneContextEnricher(routePattern);
        this.layout = Objects.requireNonNull(layout, "layout");
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
        SceneEventHandler eventHandler = new SceneEventHandler(savedContext, sceneBuilder);
        eventHandler.registerHandlers(state, subscriber, commandsEnqueue, stateUpdate);
    }

    @Override
    public void onUnmounted(ComponentCompositeKey componentId, Scene scene) {
        if (scene == null) {
            return;
        }
        // Destroy all ViewContracts to release resources (service subscriptions, etc.)
        if (scene.primaryContract() != null) {
            scene.primaryContract().onDestroy();
        }
        for (var slotEntry : scene.activeContractsBySlot().entrySet()) {
            for (var activeContract : slotEntry.getValue()) {
                activeContract.contract().onDestroy();
            }
        }
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

            if (scene.uiRegistry() == null) {
                throw new IllegalStateException("UiRegistry not found in scene");
            }

            return html(head(title(scene.pageTitle() != null ? scene.pageTitle() : "App"),
                            link(attr("rel", "stylesheet"),
                                 attr("href", "/res/style.css"))),
                    body(layout.resolve(scene, LookupFactory.create(savedContext))));
        };
    }
}
