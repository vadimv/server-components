package rsp.telemetry;

/** Dispatches explicit operational commands; authorization belongs at this boundary. */
@FunctionalInterface
public interface CommandGateway {
    <C> void dispatch(CommandKey<C> key, C command);
}
