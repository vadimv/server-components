package rsp.page;

import rsp.dom.XmlNs;
import rsp.dom.VirtualDomPath;
import rsp.dom.DefaultDomChangesPerformer;
import rsp.server.OutMessages;
import rsp.util.json.JsonDataType;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class PropertiesHandle {
    private final VirtualDomPath path;
    private final Supplier<Integer> descriptorSupplier;
    private final Map<Integer, CompletableFuture<JsonDataType>> registeredEventHandlers;
    private final OutMessages out;

    public PropertiesHandle(VirtualDomPath path,
                            Supplier<Integer> descriptorSupplier,
                            Map<Integer, CompletableFuture<JsonDataType>> registeredEventHandlers,
                            OutMessages out) {
        this.path = Objects.requireNonNull(path);
        this.descriptorSupplier = Objects.requireNonNull(descriptorSupplier);
        this.registeredEventHandlers = Objects.requireNonNull(registeredEventHandlers);
        this.out = Objects.requireNonNull(out);
    }

    /**
     * Reads a property of a DOM element
     * @param propertyName a property name
     * @return CompletableFuture of the JSON data type
     */
    public CompletableFuture<JsonDataType> get(String propertyName) {
        final Integer newDescriptor = descriptorSupplier.get();
        final CompletableFuture<JsonDataType> valueFuture = new CompletableFuture<>();
        registeredEventHandlers.put(newDescriptor, valueFuture);
        out.extractProperty(newDescriptor, path, propertyName);
        return valueFuture;
    }

    public CompletionStage<String> getString(String propertyName) {
        return get(propertyName).thenApply(v -> v.toString());
    }

    public CompletableFuture<Void> set(String propertyName, String value) {
        out.modifyDom(List.of(new DefaultDomChangesPerformer.SetAttr(path, XmlNs.html, propertyName, value, true)));
        return new CompletableFuture<>();
    }


}
