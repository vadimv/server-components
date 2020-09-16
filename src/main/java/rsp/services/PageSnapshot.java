package rsp.services;

import rsp.Event;
import rsp.dom.Path;
import rsp.dom.Tag;
import rsp.server.OutMessages;
import rsp.state.UseState;

import java.util.Map;

public class PageSnapshot {
    public final Tag currentDom;
    public final Map<Path, Event> currentEvents;
    public final Map<Object, Path> currentRefs;

    public PageSnapshot(Tag currentDom,
                        Map<Path, Event> currentEvents,
                        Map<Object, Path> currentRefs) {
        this.currentDom = currentDom;
        this.currentEvents = currentEvents;
        this.currentRefs = currentRefs;
    }
}
