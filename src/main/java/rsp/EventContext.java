package rsp;

import rsp.dom.Path;
import rsp.dsl.RefDefinition;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

public class EventContext {

    private final Map<String, CompletableFuture<?>> registeredEventHandlers;
    private final Consumer<String> sendFrontend;
    private final Supplier<Integer> descriptorSupplier;
    private final Function<Object, Path> pathLookup;

    public EventContext(Supplier<Integer> descriptorSupplier,
                        Map<String, CompletableFuture<?>> registeredEventHandlers,
                        Function<Object, Path> pathLookup,
                        Consumer<String> sendFrontend) {
        this.descriptorSupplier = descriptorSupplier;
        this.registeredEventHandlers = registeredEventHandlers;
        this.pathLookup = pathLookup;
        this.sendFrontend = sendFrontend;
    }

    public CompletableFuture<String> value(RefDefinition ref) {
        final Integer newDescriptor = descriptorSupplier.get();
        final Path path = pathLookup.apply(ref);
        final CompletableFuture<String> valueFuture = new CompletableFuture<>();
        registeredEventHandlers.put("property:" + ref.hashCode(), valueFuture);
        sendFrontend.accept("3," + path + "," + "value" + "," + newDescriptor);
        return valueFuture;
    }
}
