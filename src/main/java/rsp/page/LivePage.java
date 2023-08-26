package rsp.page;

import rsp.dom.Event;
import rsp.dom.Tag;
import rsp.dom.VirtualDomPath;
import rsp.server.Path;
import rsp.server.http.StateOriginLookup;
import rsp.util.Lookup;

import java.util.Optional;
import java.util.Set;
import java.util.function.UnaryOperator;

public interface LivePage  {
    QualifiedSessionId getId();
    Set<VirtualDomPath> updateDom(Optional<Tag> optionalOldTag, Tag newTag);
    void updateEvents(Set<Event> oldEvents, Set<Event> newEvents, Set<VirtualDomPath> elementsToRemove);
    void applyToPath(UnaryOperator<Path> pathOperator);
}
