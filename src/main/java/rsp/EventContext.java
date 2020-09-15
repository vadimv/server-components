package rsp;

import rsp.dom.Path;
import rsp.dsl.RefDefinition;
import rsp.server.OutMessages;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class EventContext {

    private final Map<String, CompletableFuture<?>> registeredEventHandlers;
    private final OutMessages out;
    private final Supplier<Integer> descriptorSupplier;
    private final Function<Object, Path> pathLookup;

    public EventContext(Supplier<Integer> descriptorSupplier,
                        Map<String, CompletableFuture<?>> registeredEventHandlers,
                        Function<Object, Path> pathLookup,
                        OutMessages out) {
        this.descriptorSupplier = descriptorSupplier;
        this.registeredEventHandlers = registeredEventHandlers;
        this.pathLookup = pathLookup;
        this.out = out;
    }

    public CompletableFuture<String> value(RefDefinition ref) {
        final Integer newDescriptor = descriptorSupplier.get();
        final Path path = pathLookup.apply(ref);
        final CompletableFuture<String> valueFuture = new CompletableFuture<>();
        registeredEventHandlers.put("property:" + ref.hashCode(), valueFuture);
        out.extractProperty(path, "value", newDescriptor);
        return valueFuture;
    }
}
