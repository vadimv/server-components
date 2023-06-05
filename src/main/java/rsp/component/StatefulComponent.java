package rsp.component;

import rsp.CreateViewFunction;
import rsp.dom.Event;
import rsp.dom.Tag;
import rsp.dom.VirtualDomPath;
import rsp.html.DocumentPartDefinition;
import rsp.page.LivePage;
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

        final DocumentPartDefinition documentPartDefinition = createViewFunction.apply(state, s -> {
            synchronized (this) {
                final Tag oldTag = tag;
                final Map<Event.Target, Event> oldEvents = events;

                state = s;

                final ComponentRenderContext crc = asComponentRenderContext(renderContext.newInstance());
                render(crc);
                final LivePage livePage = crc.livePage();
                livePage.update(oldTag, crc.tag(), oldEvents, crc.events());
            }
        });

        documentPartDefinition.render(renderContext);

        tag = renderContext.tag();
        events = renderContext.events();
    }

    private static ComponentRenderContext asComponentRenderContext(final PageRenderContext renderContext) {
        if (!(renderContext instanceof ComponentRenderContext)) throw new IllegalArgumentException("ComponentRenderContext is expected");
        return (ComponentRenderContext) renderContext;
    }
}
