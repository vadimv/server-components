package rsp;

import rsp.dsl.RefDefinition;

import java.util.Map;
import java.util.concurrent.CompletableFuture;

public class EventContext {

    private final Map<String, CompletableFuture<?>> registeredEventHandlers;

    public EventContext(Map<String, CompletableFuture<?>> registeredEventHandlers) {
        this.registeredEventHandlers = registeredEventHandlers;
    }

    public CompletableFuture<String> value(RefDefinition ref) {
        final CompletableFuture<String> valueFuture = new CompletableFuture<>();
        registeredEventHandlers.put("property:" + ref.hashCode(), valueFuture);
        return valueFuture;
    }
}
