package rsp.component;

import rsp.CreateViewFunction;
import rsp.dom.Event;
import rsp.dom.Tag;
import rsp.dom.VirtualDomPath;
import rsp.html.DocumentPartDefinition;
import rsp.page.PageRenderContext;
import rsp.server.Out;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public final class StatefulComponent<S> implements DocumentPartDefinition {
    private static final System.Logger logger = System.getLogger(StatefulComponent.class.getName());

    private final CreateViewFunction<S> createViewFunction;

    private S state;
    private VirtualDomPath path;
    private Tag tag;

    public final Map<Event.Target, Event> events = new HashMap<>();
    private List<StatefulComponent<?>> children = new ArrayList<>();

    public StatefulComponent(final S initialState,
                             final CreateViewFunction<S> createViewFunction) {
        this.state = initialState;
        this.createViewFunction = createViewFunction;
    }

    @Override
    public void render(final PageRenderContext renderContext) {
        final DefaultComponentRenderContext componentContext = new DefaultComponentRenderContext(renderContext.sharedContext(), this);

        final DocumentPartDefinition view = createViewFunction.apply(state, s -> {

            synchronized (componentContext.livePage()) {
                final Tag oldTag = tag;
                final Map<Event.Target, Event> oldEvents = Map.copyOf(events);

                state = s;

                componentContext.resetSharedContext(componentContext.newSharedContext(path));
                render(componentContext);

                componentContext.livePage().update(oldTag, componentContext.rootTag());
                componentContext.livePage().update(oldEvents, events);
            }
        });

        view.render(componentContext);

        if (path == null) {
            path = componentContext.currentTag().path;;
        }

        tag = componentContext.currentTag();
        assert path.equals(tag.path);

        if (renderContext instanceof DefaultComponentRenderContext) {
            ((DefaultComponentRenderContext)renderContext).addChildComponent(this);
        }
    }

    public void addChildComponent(StatefulComponent<?> childComponent) {
        children.add(childComponent);
    }

    public void listenEvents(Out out) {
        out.listenEvents(events.values().stream().collect(Collectors.toList()));
        children.forEach(childComponent -> childComponent.listenEvents(out));
    }

    public Map<Event.Target, Event> recursiveEvents() {
        Map<Event.Target, Event> recursiveEvents = new HashMap<>();
        recursiveEvents.putAll(events);
        for (StatefulComponent<?> childComponent : children) {
            recursiveEvents.putAll(childComponent.events);
        }
        return recursiveEvents;
    }
}
