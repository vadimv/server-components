package rsp.server;



import org.junit.jupiter.api.Test;
import rsp.dom.*;
import rsp.server.protocol.RemotePageMessageEncoder;

import java.util.List;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RemotePageMessageEncoderTests {

    @Test
    void should_set_render_num() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).setRenderNum(16);
        assertEquals("[0,16]", c.result);
    }

    @Test
    void should_listen_event() {
        final MessagesConsumer c = new MessagesConsumer();
        final DomEventEntry e = new DomEventEntry("click", new DomEventEntry.Target(TreePositionPath.of("1_1")),
                                  ec -> {},
                                  true,
                                  new DomEventEntry.DebounceModifier(100, false));
        create(c).listenEvents(List.of(e));
        assertEquals("[2,\"click\",true,\"1_1\",\"2:100:false\"]", c.result);
    }

    @Test
    void should_extract_property() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).extractProperty(32, TreePositionPath.of("1_1"), "value");
        assertEquals("[3,\"32\",\"1_1\",\"value\"]", c.result); // TODO why descriptor id is in quotes?
    }

    @Test
    void should_modify_dom_create_tag() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).modifyDom(List.of(new DefaultDomChangesContext.Create(TreePositionPath.of("1_1"), XmlNs.html, "div")));
        assertEquals("[4,0,\"1\",\"1_1\",0,\"div\"]", c.result); // TODO should a unified way to be used to encode XmlNs.html and others? e.g. an enum integer values

        create(c).modifyDom(List.of(new DefaultDomChangesContext.Create(TreePositionPath.of("100_1"), XmlNs.svg, "a")));
        assertEquals("[4,0,\"100\",\"100_1\",\"svg\",\"a\"]", c.result);
    }

    @Test
    void should_combine_modify_dom_commands_correctly() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).modifyDom(List.of(new DefaultDomChangesContext.Create(TreePositionPath.of("1_1"), XmlNs.html, "div"),
                                    new DefaultDomChangesContext.Create(TreePositionPath.of("1_1_1"), XmlNs.html, "div")));
        assertEquals("[4,0,\"1\",\"1_1\",0,\"div\",0,\"1_1\",\"1_1_1\",0,\"div\"]", c.result);
    }

    @Test
    void should_modify_dom_create_text() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).modifyDom(List.of(new DefaultDomChangesContext.CreateText(TreePositionPath.of("1_1"), TreePositionPath.of("1_1_3"), "foo bar")));
        assertEquals("[4,1,\"1_1\",\"1_1_3\",\"foo bar\"]", c.result); //TODO check escape characters
    }

    @Test
    void should_modify_dom_remove_tag() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).modifyDom(List.of(new DefaultDomChangesContext.Remove(TreePositionPath.of("1_1"), TreePositionPath.of("1_1_3"))));
        assertEquals("[4,2,\"1_1\",\"1_1_3\"]", c.result);
    }

    @Test
    void should_modify_dom_create_attr() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).modifyDom(List.of(new DefaultDomChangesContext.SetAttr(TreePositionPath.of("1_1"), XmlNs.html, "name", "value", true)));
        assertEquals("[4,3,\"1_1\",0,\"name\",\"value\",true]", c.result);
    }

    @Test
    void should_modify_dom_remove_attr() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).modifyDom(List.of(new DefaultDomChangesContext.RemoveAttr(TreePositionPath.of("1_1"), XmlNs.html, "name", false)));
        assertEquals("[4,4,\"1_1\",0,\"name\",false]", c.result);
    }

    @Test
    void should_modify_dom_create_style() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).modifyDom(List.of(new DefaultDomChangesContext.SetStyle(TreePositionPath.of("1_1"),"name", "value")));
        assertEquals("[4,5,\"1_1\",\"name\",\"value\"]", c.result);
    }

    @Test
    void should_modify_dom_remove_style() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).modifyDom(List.of(new DefaultDomChangesContext.RemoveStyle(TreePositionPath.of("1_1"),"name")));
        assertEquals("[4,6,\"1_1\",\"name\",false]", c.result); // TODO why the boolean field at the end?
    }

    @Test
    void should_set_href() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).setHref("http://localhost/foo/bar");
        assertEquals("[6,0,\"http://localhost/foo/bar\"]", c.result);
    }

    @Test
    void should_push_history() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).pushHistory("/foo/bar");
        assertEquals("[6,4,\"/foo/bar\"]", c.result);
    }

    @Test
    void should_forget_event() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).forgetEvent("click", TreePositionPath.of("1_1"));
        assertEquals("[15,\"click\",\"1_1\"]", c.result);
    }

    private RemotePageMessageEncoder create(final Consumer<String> consumer) {
        return new RemotePageMessageEncoder(consumer);
    }


    private static final class MessagesConsumer implements Consumer<String> {
        public String result;

        @Override
        public void accept(final String message) {
            result = message;
        }
    }
}
