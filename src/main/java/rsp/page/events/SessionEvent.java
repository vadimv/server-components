package rsp.page.events;

public sealed interface SessionEvent permits
        DomEvent,
        EvalJsResponseEvent,
        ExtractPropertyResponseEvent,
        GenericTaskEvent,
        InitSessionEvent,
        RemoteCommand.EvalJs,
        RemoteCommand.ExtractProperty,
        RemoteCommand.ForgetEvent,
        RemoteCommand.ListenEvent,
        RemoteCommand.ModifyDom,
        RemoteCommand.PushHistory,
        RemoteCommand.SetHref,
        RemoteCommand.SetRenderNum,
        SessionCustomEvent,
        ShutdownSessionEvent {
}
