package rsp.page;

import rsp.dom.Event;
import rsp.dom.Tag;
import rsp.dom.VirtualDomPath;
import rsp.ref.Ref;
import rsp.server.Path;

import java.util.Map;

/**
 * The current attributes of a live page.
 */
public class LivePagePropertiesSnapshot {
    public final Map<Event.Target, Event> events;
    public final Map<Ref, VirtualDomPath> refs;

    /**
     * Creates a new instance of an attributes snapshot.
     * @param path the current path
     * @param domRoot the current DOM tree root
     * @param events should be an immutable {@link Map}
     * @param refs should be an immutable {@link Map}
     */
    public LivePagePropertiesSnapshot(Map<Event.Target, Event> events,
                                      Map<Ref, VirtualDomPath> refs) {
        this.events = events;
        this.refs = refs;
    }
}
