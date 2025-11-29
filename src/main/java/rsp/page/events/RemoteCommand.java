package rsp.page.events;

import rsp.dom.DefaultDomChangesContext;
import rsp.dom.DomEventEntry;
import rsp.dom.TreePositionPath;
import rsp.server.RemoteOut;

import java.util.List;

public sealed interface RemoteCommand {

    void accept(RemoteOut remoteOut);

    record SetRenderNum(int renderNum) implements RemoteCommand, Command {
        @Override
        public void accept(RemoteOut remoteOut) {
            remoteOut.setRenderNum(renderNum);
        }
    }

    record ListenEvent(List<DomEventEntry> events) implements RemoteCommand, Command {
        @Override
        public void accept(RemoteOut remoteOut) {
            remoteOut.listenEvents(events);
        }
    }

    record ForgetEvent(String eventType, TreePositionPath elementPath) implements RemoteCommand, Command {
        @Override
        public void accept(RemoteOut remoteOut) {
            remoteOut.forgetEvent(eventType, elementPath);
        }
    }

    record ExtractProperty(int descriptor, TreePositionPath path, String name) implements RemoteCommand, Command {
        @Override
        public void accept(RemoteOut remoteOut) {
            remoteOut.extractProperty(descriptor, path, name);
        }
    }

    record ModifyDom(List<DefaultDomChangesContext.DomChange> domChanges) implements RemoteCommand, Command {
        @Override
        public void accept(RemoteOut remoteOut) {
            remoteOut.modifyDom(domChanges);
        }
    }

    record PushHistory(String path) implements RemoteCommand, Command {
        @Override
        public void accept(RemoteOut remoteOut) {
            remoteOut.pushHistory(path);
        }
    }

    record SetHref(String path) implements RemoteCommand, Command {
        @Override
        public void accept(RemoteOut remoteOut) {
            remoteOut.setHref(path);
        }
    }

    record EvalJs(int descriptor, String js) implements RemoteCommand, Command {
        @Override
        public void accept(RemoteOut remoteOut) {
            remoteOut.evalJs(descriptor, js);
        }
    }

}
