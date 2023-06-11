package rsp.component;

import rsp.page.LivePage;
import rsp.stateview.ComponentView;
import rsp.dom.Event;
import rsp.dom.Tag;
import rsp.dom.VirtualDomPath;
import rsp.html.SegmentDefinition;
import rsp.page.RenderContext;
import rsp.ref.Ref;
import rsp.server.Out;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public final class Component<S> implements SegmentDefinition {
    private static final System.Logger logger = System.getLogger(Component.class.getName());

    private final ComponentView<S> componentView;

    private S state;
    private VirtualDomPath path;
    private Tag tag;

    // TODO change to private
    public final Map<Event.Target, Event> events = new HashMap<>();
    public final Map<Ref, VirtualDomPath> refs = new ConcurrentHashMap<>();
    private List<Component<?>> children = new ArrayList<>();

    public Component(final S initialState,
                     final ComponentView<S> componentView) {
        this.state = initialState;
        this.componentView = componentView;
    }

    @Override
    public void render(final RenderContext renderContext) {
        final DefaultComponentRenderContext<S> componentContext = new DefaultComponentRenderContext<>(renderContext.sharedContext(), this);

        final SegmentDefinition view = componentView.apply(state).apply(s -> {
            final LivePage livePage = componentContext.livePage();
            synchronized (livePage) {
                final Tag oldTag = tag;
                final Map<Event.Target, Event> oldEvents = Map.copyOf(events);

                state = s;

                componentContext.resetSharedContext(componentContext.newSharedContext(path));
                render(componentContext);

                livePage.update(oldTag, componentContext.rootTag());
                livePage.update(oldEvents, events);
            }
        });

        view.render(componentContext);

        if (path == null) {
            path = componentContext.currentTag().path;;
        }

        tag = componentContext.currentTag();
        assert path.equals(tag.path);

        if (renderContext instanceof DefaultComponentRenderContext) {
            ((DefaultComponentRenderContext<?>)renderContext).addChildComponent(this);
        }
    }

    public void addChildComponent(Component<?> childComponent) {
        children.add(childComponent);
    }

    public void listenEvents(Out out) {
        out.listenEvents(events.values().stream().collect(Collectors.toList()));
        children.forEach(childComponent -> childComponent.listenEvents(out));
    }

    public Map<Event.Target, Event> recursiveEvents() {
        final Map<Event.Target, Event> recursiveEvents = new HashMap<>();
        recursiveEvents.putAll(events);
        for (Component<?> childComponent : children) {
            recursiveEvents.putAll(childComponent.events);
        }
        return recursiveEvents;
    }

    public Map<Ref, VirtualDomPath> recursiveRefs() {
        final Map<Ref, VirtualDomPath> recursiveRefs = new HashMap<>();
        recursiveRefs.putAll(refs);
        for (Component<?> childComponent : children) {
            recursiveRefs.putAll(childComponent.refs);
        }
        return recursiveRefs;
    }
}
