package rsp.server;

import rsp.dom.DefaultDomChangesContext;
import rsp.dom.Event;
import rsp.dom.TreePositionPath;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class TestCollectingRemoteOut implements RemoteOut {
    public final List<Message> commands = new ArrayList<>();

    @Override
    public void setRenderNum(final int renderNum) {
        commands.add(new SetRenderNumOutMessage(renderNum));
    }

    @Override
    public void listenEvents(final List<Event> events) {
        commands.addAll(events.stream().map(e -> new ListenEventOutMessage(e.eventTarget.eventType(),
                                                                           e.preventDefault,
                                                                           e.eventTarget.elementPath(),
                                                                           e.modifier)).toList());
    }

    @Override
    public void forgetEvent(final String eventType, final TreePositionPath elementPath) {
        commands.add(new ForgetEventOutMessage(eventType, elementPath));

    }

    @Override
    public void extractProperty(final int descriptor, final TreePositionPath path, final String name) {
        commands.add(new ExtractPropertyOutMessage(descriptor, path, name));
    }

    @Override
    public void modifyDom(final List<DefaultDomChangesContext.DomChange> domChange) {
        commands.add(new ModifyDomOutMessage(domChange));
    }

    @Override
    public void setHref(final String path) {
        throw new IllegalStateException();
    }

    @Override
    public void pushHistory(final String path) {
        commands.add(new PushHistoryMessage(path));
    }

    @Override
    public void evalJs(final int descriptor, final String js) {
        commands.add(new EvalJsMessage(descriptor, js));
    }

    public void clear() {
        commands.clear();
    }

    public sealed interface Message {}

    public record SetRenderNumOutMessage(int renderNum) implements Message {
    }

    public record ListenEventOutMessage(String eventType, boolean preventDefault, TreePositionPath path, Event.Modifier modifier) implements Message {

        @Override
        public String toString() {
            return "ListenEventOutMessage{" +
                    "eventType='" + eventType + '\'' +
                    ", preventDefault=" + preventDefault +
                    ", componentPath=" + path +
                    ", modifier=" + modifier +
                    '}';
        }
    }

    public record ForgetEventOutMessage(String eventType, TreePositionPath elementPath) implements Message {

        @Override
        public String toString() {
            return "ForgetEventOutMessage{" +
                    "eventType='" + eventType + '\'' +
                    ", elementPath=" + elementPath +
                    '}';
        }
    }

    public record ExtractPropertyOutMessage(int descriptor, TreePositionPath path, String name) implements Message {

    }

    public static final class ModifyDomOutMessage implements Message {
        public final List<DefaultDomChangesContext.DomChange> domChange;

        ModifyDomOutMessage(final List<DefaultDomChangesContext.DomChange> domChange) {
            this.domChange = domChange;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final ModifyDomOutMessage that = (ModifyDomOutMessage) o;
            return Objects.equals(domChange, that.domChange);
        }

        @Override
        public int hashCode() {
            return Objects.hash(domChange);
        }

        @Override
        public String toString() {
            return "ModifyDomOutMessage{" +
                    "domChange=" + domChange +
                    '}';
        }
    }

    public record PushHistoryMessage(String path) implements Message {

        @Override
        public String toString() {
            return "PushHistoryMessage{" +
                    "componentPath='" + path + '\'' +
                    '}';
        }
    }

    public record EvalJsMessage(int descriptor, String js) implements Message {

        @Override
        public String toString() {
            return "EvalJsMessage{" +
                    "descriptor=" + descriptor +
                    ", js='" + js + '\'' +
                    '}';
        }
    }
}
