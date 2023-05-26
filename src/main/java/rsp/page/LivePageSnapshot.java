package rsp.page;

import rsp.StatefulComponent;
import rsp.dom.Event;
import rsp.dom.Tag;
import rsp.dom.VirtualDomPath;
import rsp.ref.Ref;
import rsp.server.Path;

import java.util.Map;

/**
 * The current attributes of a live page.
 */
public class LivePageSnapshot<S> {
    public final StatefulComponent<S> rootComponent;
    public final StateNotificationListener componentsStateNotificationListener;
    public final Path path;
    public final Tag domRoot;
    public final Map<Event.Target, Event> events;
    public final Map<Ref, VirtualDomPath> refs;

    /**
     * Creates a new instance of a snapshot.
     * @param path the current path
     * @param domRoot the current DOM tree root
     * @param events should be an immutable {@link Map}
     * @param refs should be an immutable {@link Map}
     */
    public LivePageSnapshot(StatefulComponent<S> rootComponent,
                            StateNotificationListener componentsStateNotificationListener,
                            Path path,
                            Tag domRoot,
                            Map<Event.Target, Event> events,
                            Map<Ref, VirtualDomPath> refs) {
        this.rootComponent = rootComponent;
        this.componentsStateNotificationListener = componentsStateNotificationListener;
        this.path = path;
        this.domRoot = domRoot;
        this.events = events;
        this.refs = refs;
    }
}
