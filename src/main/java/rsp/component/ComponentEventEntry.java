package rsp.component;

import rsp.util.json.JsonDataType;

import java.util.Objects;
import java.util.function.Consumer;

public record ComponentEventEntry(String eventName, Consumer<EventContext> eventHandler, boolean preventDefault) {

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final ComponentEventEntry event = (ComponentEventEntry) o;
        // ignore eventHandler
        return Objects.equals(eventName, event.eventName) &&
                preventDefault == event.preventDefault;
    }

    @Override
    public int hashCode() {
        //ignore eventHandler
        return Objects.hash(eventName, preventDefault);
    }

    public record EventContext(JsonDataType.Object eventObject) {

    }
}
