package rsp.page.events;

public sealed interface SessionEvent permits DomEvent, EvalJsResponseEvent, ExtractPropertyResponseEvent, GenericTaskEvent, InitSessionEvent, SessionCustomEvent, ShutdownSessionEvent {
}
