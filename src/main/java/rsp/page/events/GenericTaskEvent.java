package rsp.page.events;

public record GenericTaskEvent(Runnable task) implements SessionEvent {
}
