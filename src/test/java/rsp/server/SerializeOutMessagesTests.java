package rsp.server;

import org.junit.Assert;
import org.junit.Test;
import rsp.dom.DefaultDomChangesPerformer;
import rsp.dom.Event;
import rsp.dom.VirtualDomPath;
import rsp.dom.XmlNs;

import java.util.List;
import java.util.function.Consumer;

public class SerializeOutMessagesTests {

    @Test
    public void should_set_render_num() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).setRenderNum(16);
        Assert.assertEquals("[0,16]", c.result);
    }

    @Test
    public void should_listen_event() {
        final MessagesConsumer c = new MessagesConsumer();
        final Event e = new Event(new Event.Target("click", VirtualDomPath.of("1_1")),
                                  ec -> {},
                                  true,
                                  new Event.DebounceModifier(100, false));
        create(c).listenEvents(List.of(e));
        Assert.assertEquals("[2,\"click\",true,\"1_1\",\"2:100:false\"]", c.result);
    }

    @Test
    public void should_extract_property() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).extractProperty(32, VirtualDomPath.of("1_1"), "value");
        Assert.assertEquals("[3,\"32\",\"1_1\",\"value\"]", c.result); // TODO why descriptor id is in quotes?
    }

    @Test
    public void should_modify_dom_create_tag() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).modifyDom(List.of(new DefaultDomChangesPerformer.Create(VirtualDomPath.of("1_1"), XmlNs.html, "div")));
        Assert.assertEquals("[4,0,\"1\",\"1_1\",0,\"div\"]", c.result); // TODO should a unified way to be used to encode XmlNs.html and others? e.g. an enum integer values

        create(c).modifyDom(List.of(new DefaultDomChangesPerformer.Create(VirtualDomPath.of("100_1"), XmlNs.svg, "a")));
        Assert.assertEquals("[4,0,\"100\",\"100_1\",\"svg\",\"a\"]", c.result);
    }

    @Test
    public void should_combine_modify_dom_commands_correctly() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).modifyDom(List.of(new DefaultDomChangesPerformer.Create(VirtualDomPath.of("1_1"), XmlNs.html, "div"),
                                    new DefaultDomChangesPerformer.Create(VirtualDomPath.of("1_1_1"), XmlNs.html, "div")));
        Assert.assertEquals("[4,0,\"1\",\"1_1\",0,\"div\",0,\"1_1\",\"1_1_1\",0,\"div\"]", c.result);
    }

    @Test
    public void should_modify_dom_create_text() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).modifyDom(List.of(new DefaultDomChangesPerformer.CreateText(VirtualDomPath.of("1_1"), VirtualDomPath.of("1_1_3"), "foo bar")));
        Assert.assertEquals("[4,1,\"1_1\",\"1_1_3\",\"foo bar\"]", c.result); //TODO check escape characters
    }

    @Test
    public void should_modify_dom_remove_tag() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).modifyDom(List.of(new DefaultDomChangesPerformer.Remove(VirtualDomPath.of("1_1"), VirtualDomPath.of("1_1_3"))));
        Assert.assertEquals("[4,2,\"1_1\",\"1_1_3\"]", c.result);
    }

    @Test
    public void should_modify_dom_create_attr() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).modifyDom(List.of(new DefaultDomChangesPerformer.SetAttr(VirtualDomPath.of("1_1"), XmlNs.html, "name", "value", true)));
        Assert.assertEquals("[4,3,\"1_1\",0,\"name\",\"value\",true]", c.result);
    }

    @Test
    public void should_modify_dom_remove_attr() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).modifyDom(List.of(new DefaultDomChangesPerformer.RemoveAttr(VirtualDomPath.of("1_1"), XmlNs.html, "name", false)));
        Assert.assertEquals("[4,4,\"1_1\",0,\"name\",false]", c.result);
    }

    @Test
    public void should_modify_dom_create_style() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).modifyDom(List.of(new DefaultDomChangesPerformer.SetStyle(VirtualDomPath.of("1_1"),"name", "value")));
        Assert.assertEquals("[4,5,\"1_1\",\"name\",\"value\"]", c.result);
    }

    @Test
    public void should_modify_dom_remove_style() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).modifyDom(List.of(new DefaultDomChangesPerformer.RemoveStyle(VirtualDomPath.of("1_1"),"name")));
        Assert.assertEquals("[4,6,\"1_1\",\"name\",false]", c.result); // TODO why the boolean field at the end?
    }

    @Test
    public void should_set_href() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).setHref("http://localhost/foo/bar");
        Assert.assertEquals("[6,0,\"http://localhost/foo/bar\"]", c.result);
    }

    @Test
    public void should_push_history() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).pushHistory("/foo/bar");
        Assert.assertEquals("[6,4,\"/foo/bar\"]", c.result);
    }

    @Test
    public void should_forget_event() {
        final MessagesConsumer c = new MessagesConsumer();
        create(c).forgetEvent("click", VirtualDomPath.of("1_1"));
        Assert.assertEquals("[15,\"click\",\"1_1\"]", c.result);
    }

    private SerializeOutMessages create(final Consumer<String> consumer) {
        return new SerializeOutMessages(consumer);
    }


    private static final class MessagesConsumer implements Consumer<String> {
        public String result;

        @Override
        public void accept(final String message) {
            result = message;
        }
    }
}
