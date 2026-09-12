package rsp.page.events;

import rsp.dom.DefaultDomChangesContext;
import rsp.dom.DomEventEntry;
import rsp.dom.NodeId;
import rsp.server.RemoteOut;

import java.util.List;
import java.util.Objects;

/**
 * Commands to the client-side running in the browser.
 */
public sealed interface RemoteCommand {

    /**
     *
     * @param remoteOut
     */
    void accept(RemoteOut remoteOut);

    /**
     * An ordered, general-purpose group of remote commands.
     */
    record Batch(List<RemoteCommand> commands) implements RemoteCommand, Command {
        public Batch {
            commands = List.copyOf(Objects.requireNonNull(commands));
        }

        @Override
        public void accept(final RemoteOut remoteOut) {
            Objects.requireNonNull(remoteOut).batch(out -> commands.forEach(command -> command.accept(out)));
        }
    }

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

    record ForgetEvent(String eventType, NodeId nodeId) implements RemoteCommand, Command {
        @Override
        public void accept(RemoteOut remoteOut) {
            remoteOut.forgetEvent(eventType, nodeId);
        }
    }

    record ExtractProperty(int descriptor, NodeId nodeId, String name) implements RemoteCommand, Command {
        @Override
        public void accept(RemoteOut remoteOut) {
            remoteOut.extractProperty(descriptor, nodeId, name);
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

    record ShowModal(NodeId nodeId) implements RemoteCommand, Command {
        public ShowModal {
            java.util.Objects.requireNonNull(nodeId, "nodeId");
        }

        @Override
        public void accept(RemoteOut remoteOut) {
            remoteOut.showModal(nodeId);
        }
    }

    record EvalJs(int descriptor, String js) implements RemoteCommand, Command {
        @Override
        public void accept(RemoteOut remoteOut) {
            remoteOut.evalJs(descriptor, js);
        }
    }

}
