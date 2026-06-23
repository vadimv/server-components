package rsp.page.events;

public sealed interface Command permits
        DomEventNotification,
        ComponentEventNotification,
        EvalJsResponseEvent,
        ExtractPropertyResponseEvent,
        GenericTaskEvent,
        InitSessionCommand,
        RemoteCommand.EvalJs,
        RemoteCommand.ExtractProperty,
        RemoteCommand.ForgetEvent,
        RemoteCommand.ListenEvent,
        RemoteCommand.ModifyDom,
        RemoteCommand.PushHistory,
        RemoteCommand.SetHref,
        RemoteCommand.SetRenderNum,
        SessionCustomEvent,
        ShutdownSessionCommand {
}
