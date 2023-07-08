package rsp.page;

import rsp.dom.Event;
import rsp.dom.Tag;
import rsp.dom.VirtualDomPath;
import rsp.server.Path;
import rsp.util.Lookup;
import rsp.util.json.JsonDataType;

import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.function.UnaryOperator;

public interface LivePage extends Lookup {
    CompletableFuture<JsonDataType> evalJs(String js);
    Set<VirtualDomPath> updateDom(Optional<Tag> optionalOldTag, Tag newTag);
    void updateEvents(Set<Event> oldEvents, Set<Event> newEvents, Set<VirtualDomPath> elementsToRemove);
    void applyToPath(UnaryOperator<Path> pathOperator);
}
