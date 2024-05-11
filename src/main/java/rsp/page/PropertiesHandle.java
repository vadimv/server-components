package rsp.page;

import rsp.dom.XmlNs;
import rsp.dom.TreePositionPath;
import rsp.dom.DefaultDomChangesContext;
import rsp.server.RemoteOut;
import rsp.util.json.JsonDataType;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

public final class PropertiesHandle {
    private final TreePositionPath path;
    private final Supplier<Integer> descriptorSupplier;
    private final Map<Integer, CompletableFuture<JsonDataType>> registeredEventHandlers;
    private final RemoteOut remoteOut;

    public PropertiesHandle(final TreePositionPath path,
                            final Supplier<Integer> descriptorSupplier,
                            final Map<Integer, CompletableFuture<JsonDataType>> registeredEventHandlers,
                            final RemoteOut remoteOut) {
        this.path = Objects.requireNonNull(path);
        this.descriptorSupplier = Objects.requireNonNull(descriptorSupplier);
        this.registeredEventHandlers = Objects.requireNonNull(registeredEventHandlers);
        this.remoteOut = Objects.requireNonNull(remoteOut);
    }

    /**
     * Reads a property of a DOM element
     * @param propertyName a property name
     * @return CompletableFuture of the JSON data type
     */
    public CompletableFuture<JsonDataType> get(final String propertyName) {
        final Integer newDescriptor = descriptorSupplier.get();
        final CompletableFuture<JsonDataType> valueFuture = new CompletableFuture<>();
        registeredEventHandlers.put(newDescriptor, valueFuture);
        remoteOut.extractProperty(newDescriptor, path, propertyName);
        return valueFuture;
    }

    public CompletionStage<String> getString(final String propertyName) {
        return get(propertyName).thenApply(v -> v.toString());
    }

    public CompletableFuture<Void> set(final String propertyName, final String value) {
        remoteOut.modifyDom(List.of(new DefaultDomChangesContext.SetAttr(path, XmlNs.html, propertyName, value, true)));
        return new CompletableFuture<>();
    }


}
