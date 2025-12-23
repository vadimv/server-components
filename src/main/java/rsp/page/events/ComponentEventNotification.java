package rsp.page.events;

/**
 * A command representing a notification for a component event.
 * This is used for component-to-component communication.
 *
 * @param eventType the type of the event
 * @param eventObject the data payload of the event
 */
public record ComponentEventNotification(String eventType, Object eventObject) implements Command {
}
