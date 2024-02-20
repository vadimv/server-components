package rsp.page;

import rsp.dom.DefaultDomChangesContext;
import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.server.RemoteOut;

import java.util.List;

public sealed interface PageCommand {

    void accept(RemoteOut remoteOut);

    record SetRenderNum(int renderNum) implements PageCommand {
        @Override
        public void accept(RemoteOut remoteOut) {
            remoteOut.setRenderNum(renderNum);
        }
    }

    record ListenEvent(List<Event> events) implements PageCommand {
        @Override
        public void accept(RemoteOut remoteOut) {
            remoteOut.listenEvents(events);
        }
    }

    record ForgetEvent(String eventType, VirtualDomPath elementPath) implements PageCommand {
        @Override
        public void accept(RemoteOut remoteOut) {
            remoteOut.forgetEvent(eventType, elementPath);
        }
    }

    record ExtractProperty(int descriptor, VirtualDomPath path, String name) implements PageCommand {
        @Override
        public void accept(RemoteOut remoteOut) {
            remoteOut.extractProperty(descriptor, path, name);
        }
    }

    record ModifyDom(List<DefaultDomChangesContext.DomChange> domChanges) implements PageCommand {
        @Override
        public void accept(RemoteOut remoteOut) {
            remoteOut.modifyDom(domChanges);
        }
    }

    record PushHistory(String path) implements PageCommand {
        @Override
        public void accept(RemoteOut remoteOut) {
            remoteOut.pushHistory(path);
        }
    }

    record SetHref(String path) implements PageCommand {
        @Override
        public void accept(RemoteOut remoteOut) {
            remoteOut.setHref(path);
        }
    }

    record EvalJs(int descriptor, String js) implements PageCommand {
        @Override
        public void accept(RemoteOut remoteOut) {
            remoteOut.evalJs(descriptor, js);
        }
    }

}
