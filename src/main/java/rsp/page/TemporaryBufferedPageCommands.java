package rsp.page;

import rsp.dom.DefaultDomChangesContext;
import rsp.dom.Event;
import rsp.dom.TreePositionPath;
import rsp.server.RemoteOut;

import java.util.*;

public final class TemporaryBufferedPageCommands implements RemoteOut {

    private final Queue<PageCommand> queue = new ArrayDeque<>();

    private RemoteOut remoteOut;

    public TemporaryBufferedPageCommands() {
        synchronized (this) {
            this.remoteOut = new RemoteOut() {
                @Override
                public void setRenderNum(final int renderNum) {
                    queue.add(new PageCommand.SetRenderNum(renderNum));
                }

                @Override
                public void listenEvents(final List<Event> events) {
                    queue.add(new PageCommand.ListenEvent(events));
                }

                @Override
                public void forgetEvent(final String eventType, final TreePositionPath elementPath) {
                    queue.add(new PageCommand.ForgetEvent(eventType, elementPath));
                }

                @Override
                public void extractProperty(final int descriptor, final TreePositionPath path, final String name) {
                    queue.add(new PageCommand.ExtractProperty(descriptor, path, name));
                }

                @Override
                public void modifyDom(final List<DefaultDomChangesContext.DomChange> domChanges) {
                    queue.add(new PageCommand.ModifyDom(domChanges));
                }

                @Override
                public void setHref(final String path) {
                    queue.add(new PageCommand.SetHref(path));
                }

                @Override
                public void pushHistory(final String path) {
                    queue.add(new PageCommand.PushHistory(path));
                }

                @Override
                public void evalJs(final int descriptor, final String js) {
                    queue.add(new PageCommand.EvalJs(descriptor, js));
                }
            };
        }
    }

    public synchronized void redirectMessagesOut(final RemoteOut directRemoteOut) {
        remoteOut = Objects.requireNonNull(directRemoteOut);
        while (!queue.isEmpty()) {
            final PageCommand command = queue.remove();
            command.accept(remoteOut);
        }
    }

    @Override
    public synchronized void setRenderNum(final int renderNum) {
        remoteOut.setRenderNum(renderNum);
    }

    @Override
    public synchronized void listenEvents(final List<Event> events) {
        remoteOut.listenEvents(events);
    }

    @Override
    public synchronized void forgetEvent(final String eventType, final TreePositionPath elementPath) {
        remoteOut.forgetEvent(eventType, elementPath);
    }

    @Override
    public synchronized void extractProperty(final int descriptor, final TreePositionPath path, final String name) {
        remoteOut.extractProperty(descriptor, path, name);
    }

    @Override
    public synchronized void modifyDom(final List<DefaultDomChangesContext.DomChange> domChange) {
        remoteOut.modifyDom(domChange);
    }

    @Override
    public synchronized void setHref(final String path) {
        remoteOut.setHref(path);
    }

    @Override
    public synchronized void pushHistory(final String path) {
        remoteOut.pushHistory(path);
    }

    @Override
    public synchronized void evalJs(final int descriptor, final String js) {
        remoteOut.evalJs(descriptor, js);
    }
}
