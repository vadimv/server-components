package rsp.component;

public record ComponentCallbacks<S>(ComponentMountedCallback<S> componentMountedCallback,
                                    ComponentUpdatedCallback<S> componentUpdatedCallback,
                                    ComponentUnmountedCallback<S> componentUnmountedCallback) {}
