package rsp.page;

import nl.jqno.equalsverifier.EqualsVerifier;
import org.junit.Assert;
import org.junit.Test;
import rsp.Component;
import rsp.dom.*;
import rsp.server.OutMessages;
import rsp.server.Path;
import rsp.state.ReadOnly;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Collectors;

import static rsp.dsl.Html.*;

public class LivePageStateTests {

    private static final QualifiedSessionId QID = new QualifiedSessionId("1", "1");

    @Test
    public void should_generate_update_commands_for_same_state() {
        final TestCollectingOutMessages out = new TestCollectingOutMessages();
        final LivePageState<State> livePageState = create(new State(0), out);
        livePageState.accept(new State(0));
        Assert.assertEquals(List.of(), out.commands);
    }

    @Test
    public void should_generate_update_commands_for_new_state() {
        final TestCollectingOutMessages out = new TestCollectingOutMessages();
        final LivePageState<State> livePageState = create(new State(0), out);
        livePageState.accept(new State(100));
        Assert.assertEquals(List.of(new ModifyDomOutMessage(List.of(new DefaultDomChangesPerformer.CreateText(VirtualDomPath.of("1"),
                                                                                                      VirtualDomPath.of("1_1"),
                                                                                                      "100"))),
                                    new PushHistoryMessage("/100")),
                            out.commands);
    }

    private LivePageState<State> create(State state, OutMessages out) {
        final Component<State> rootComponent = s -> span(s.get().toString());

        final LivePagePropertiesSnapshot lpps = new LivePagePropertiesSnapshot(Path.of("/" + state),
                                                                               domRoot(rootComponent, state),
                                                                               Collections.emptyMap(),
                                                                               Collections.emptyMap());

        final StateToRouteDispatch<State> state2route = new StateToRouteDispatch<>(Path.of(""), s -> Path.of("/" + s.value));

        return new LivePageState<>(lpps, QID, state2route, rootComponent, enrichFunction(), out);
    }

    private static Tag domRoot(Component<State> component, State state) {
        final DomTreePageRenderContext domTreeContext = new DomTreePageRenderContext();
        component.render(new ReadOnly<>(state)).accept(enrichFunction().apply(QID.sessionId, domTreeContext));

        return domTreeContext.root();
    }

    private static BiFunction<String, PageRenderContext, PageRenderContext> enrichFunction() {
        return (sessionId, ctx) -> ctx;
    }

    @Test
    public void should_comply_to_equals_hash_contract_for_helper_classes() {
        EqualsVerifier.forClass(SetRenderNumOutMessage.class).verify();
        EqualsVerifier.forClass(ListenEventOutMessage.class).verify();
        EqualsVerifier.forClass(ForgetEventOutMessage.class).verify();
        EqualsVerifier.forClass(ExtractPropertyOutMessage.class).verify();
        EqualsVerifier.forClass(ModifyDomOutMessage.class).verify();
        EqualsVerifier.forClass(PushHistoryMessage.class).verify();
    }

    private static class State {
        public final int value;

        private State(int value) {
            this.value = value;
        }

        @Override
        public String toString() {
            return Integer.toString(value);
        }
    }

    private static class TestCollectingOutMessages implements OutMessages {
        public final List commands = new ArrayList();

        @Override
        public void setRenderNum(int renderNum) {
            commands.add(new SetRenderNumOutMessage(renderNum));
        }

        @Override
        public void listenEvents(List<Event> events) {
                commands.addAll(events.stream().map(e -> new ListenEventOutMessage(e.eventTarget.eventType,
                                                                                   e.preventDefault,
                                                                                   e.eventTarget.elementPath,
                                                                                   e.modifier)).collect(Collectors.toList()));
        }

        @Override
        public void forgetEvent(String eventType, VirtualDomPath elementPath) {
            commands.add(new ForgetEventOutMessage(eventType, elementPath));

        }

        @Override
        public void extractProperty(int descriptor, VirtualDomPath path, String name) {
            commands.add(new ExtractPropertyOutMessage(descriptor, path, name));
        }

        @Override
        public void modifyDom(List<DefaultDomChangesPerformer.DomChange> domChange) {
            commands.add(new ModifyDomOutMessage(domChange));
        }

        @Override
        public void setHref(String path) {
            throw new IllegalStateException();
        }

        @Override
        public void pushHistory(String path) {
            commands.add(new PushHistoryMessage(path));
        }

        @Override
        public void evalJs(int descriptor, String js) {
            throw new IllegalStateException();
        }
    }

    final static class SetRenderNumOutMessage {
        public final int renderNum;
        SetRenderNumOutMessage(int renderNum) {
            this.renderNum = renderNum;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            SetRenderNumOutMessage that = (SetRenderNumOutMessage) o;
            return renderNum == that.renderNum;
        }

        @Override
        public int hashCode() {
            return Objects.hash(renderNum);
        }
    }

    final static class ListenEventOutMessage {
        public final String eventType;
        public final boolean preventDefault;
        public final VirtualDomPath path;
        public final Event.Modifier modifier;

        public ListenEventOutMessage(String eventType, boolean preventDefault, VirtualDomPath path, Event.Modifier modifier) {
            this.eventType = eventType;
            this.preventDefault = preventDefault;
            this.path = path;
            this.modifier = modifier;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ListenEventOutMessage that = (ListenEventOutMessage) o;
            return preventDefault == that.preventDefault &&
                    Objects.equals(eventType, that.eventType) &&
                    Objects.equals(path, that.path) &&
                    Objects.equals(modifier, that.modifier);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventType, preventDefault, path, modifier);
        }
    }

    static final class ForgetEventOutMessage {
        public final String eventType;
        public final VirtualDomPath elementPath;

        public ForgetEventOutMessage(String eventType, VirtualDomPath elementPath) {
            this.eventType = eventType;
            this.elementPath = elementPath;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ForgetEventOutMessage that = (ForgetEventOutMessage) o;
            return Objects.equals(eventType, that.eventType) &&
                    Objects.equals(elementPath, that.elementPath);
        }

        @Override
        public int hashCode() {
            return Objects.hash(eventType, elementPath);
        }
    }

    static final class ExtractPropertyOutMessage {
        public final int descriptor;
        public final VirtualDomPath path;
        public final String name;

        public ExtractPropertyOutMessage(int descriptor, VirtualDomPath path, String name) {
            this.descriptor = descriptor;
            this.path = path;
            this.name = name;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ExtractPropertyOutMessage that = (ExtractPropertyOutMessage) o;
            return descriptor == that.descriptor &&
                    Objects.equals(path, that.path) &&
                    Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(descriptor, path, name);
        }
    }

    static final class ModifyDomOutMessage {
        public final List<DefaultDomChangesPerformer.DomChange> domChange;

        ModifyDomOutMessage(List<DefaultDomChangesPerformer.DomChange> domChange) {
            this.domChange = domChange;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            ModifyDomOutMessage that = (ModifyDomOutMessage) o;
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

    static final class PushHistoryMessage {
        public final String path;

        public PushHistoryMessage(String path) {
            this.path = path;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final PushHistoryMessage that = (PushHistoryMessage) o;
            return Objects.equals(path, that.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path);
        }

        @Override
        public String toString() {
            return "PushHistoryMessage{" +
                    "path='" + path + '\'' +
                    '}';
        }
    }
}
