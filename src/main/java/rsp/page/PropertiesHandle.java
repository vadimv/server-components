package rsp.page;

import rsp.dom.XmlNs;
import rsp.dom.Path;
import rsp.dom.RemoteDomChangesPerformer;
import rsp.server.OutMessages;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class PropertiesHandle {
    private final Path path;
    private final Supplier<Integer> descriptorSupplier;
    private final Map<Integer, CompletableFuture<Object>> registeredEventHandlers;
    private final OutMessages out;

    public PropertiesHandle(Path path,
                            Supplier<Integer> descriptorSupplier,
                            Map<Integer, CompletableFuture<Object>> registeredEventHandlers,
                            OutMessages out) {
        this.path = Objects.requireNonNull(path);
        this.descriptorSupplier = Objects.requireNonNull(descriptorSupplier);
        this.registeredEventHandlers = Objects.requireNonNull(registeredEventHandlers);
        this.out = Objects.requireNonNull(out);
    }

    /**
     * Reads a property of a DOM element
     * @param propertyName a property name
     * @returna CompletableFuture of either a Map, String, Long, Double or Boolean according to its evaluation result JSON data type
     */
    public CompletableFuture<Object> get(String propertyName) {
        final Integer newDescriptor = descriptorSupplier.get();
        final CompletableFuture<Object> valueFuture = new CompletableFuture<>();
        registeredEventHandlers.put(newDescriptor, valueFuture);
        out.extractProperty(newDescriptor, path, propertyName);
        return valueFuture;
    }

    public CompletionStage<String> getString(String propertyName) {
        return get(propertyName).thenApply(v -> v.toString());
    }

    public CompletableFuture<Void> set(String propertyName, String value) {
        out.modifyDom(List.of(new RemoteDomChangesPerformer.SetAttr(path, XmlNs.html, propertyName, value, true)));
        return new CompletableFuture<>();
    }


}
