package rsp.component;

import rsp.CreateViewFunction;
import rsp.dom.Event;
import rsp.dom.Tag;
import rsp.dom.VirtualDomPath;
import rsp.html.DocumentPartDefinition;
import rsp.page.PageRenderContext;

import java.util.Map;

public final class StatefulComponent<S> implements DocumentPartDefinition {
    private static final System.Logger logger = System.getLogger(StatefulComponent.class.getName());

    public final CreateViewFunction<S> createViewFunction;

    private S state;
    private VirtualDomPath path;
    private Tag tag;
    public Map<Event.Target, Event> events;

    public StatefulComponent(final S initialState,
                             final CreateViewFunction<S> createViewFunction) {
        this.state = initialState;
        this.createViewFunction = createViewFunction;
    }


    @Override
    public void render(final PageRenderContext renderContext) {
        //path = renderContext.tag() == null ? renderContext.rootPath() : renderContext.tag().path();

        final DocumentPartDefinition view = createViewFunction.apply(state, s -> {
            final PageRenderContext componentContext = renderContext.newInstance();
            assert componentContext instanceof ComponentRenderContext;
            final ComponentRenderContext context = (ComponentRenderContext) componentContext;

            synchronized (context.livePage()) {
                final Tag oldTag = tag;
                final Map<Event.Target, Event> oldEvents = events;

                state = s;

                render(context);
                context.livePage().update(oldTag, context.tag(), oldEvents, context.events());
            }
        });

        view.render(renderContext);

        tag = renderContext.tag();
        events = renderContext.events();
    }
}
