package rsp.page;

import rsp.dom.XmlNs;
import rsp.dom.TreePositionPath;
import rsp.dom.DefaultDomChangesContext;
import rsp.page.events.RemoteCommand;
import rsp.page.events.Command;
import rsp.util.json.JsonDataType;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.function.Consumer;
import java.util.function.Supplier;

public final class PropertiesHandle {
    private final TreePositionPath path;
    private final Supplier<Integer> descriptorSupplier;
    private final Map<Integer, CompletableFuture<JsonDataType>> registeredEventHandlers;
    private final Consumer<Command> remoteOut;

    public PropertiesHandle(final TreePositionPath path,
                            final Supplier<Integer> descriptorSupplier,
                            final Map<Integer, CompletableFuture<JsonDataType>> registeredEventHandlers,
                            final Consumer<Command> remoteOut) {
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
        Objects.requireNonNull(propertyName);
        final Integer newDescriptor = descriptorSupplier.get();
        final CompletableFuture<JsonDataType> valueFuture = new CompletableFuture<>();
        registeredEventHandlers.put(newDescriptor, valueFuture);
        remoteOut.accept(new RemoteCommand.ExtractProperty(newDescriptor, path, propertyName));
        return valueFuture;
    }

    public CompletionStage<String> getString(final String propertyName) {
        Objects.requireNonNull(propertyName);
        return get(propertyName).thenApply(v -> v.toString());
    }

    public CompletableFuture<Void> set(final String propertyName, final String value) {
        Objects.requireNonNull(propertyName);
        Objects.requireNonNull(value);
        remoteOut.accept(new RemoteCommand.ModifyDom(List.of(new DefaultDomChangesContext.SetAttr(path, XmlNs.html, propertyName, value, true))));
        return new CompletableFuture<>();
    }


}
