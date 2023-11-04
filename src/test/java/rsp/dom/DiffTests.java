package rsp.dom;

import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DiffTests {
    final VirtualDomPath path = new VirtualDomPath(1);

    @Test
    public void should_be_empty_diff_for_same_single_tags() {
        final Tag tree1 = new Tag(path, XmlNs.html, "html");
        final Tag tree2 = new Tag(path, XmlNs.html, "html");

        final TestChangesContext cp = new TestChangesContext();
        new Diff(Optional.of(tree1), tree2, cp).run();
        assertEquals("", cp.resultAsString());
    }

    @Test
    public void should_remove_and_create_for_different_single_tags() {
        final Tag tree1 = new Tag(path, XmlNs.html, "html");
        final Tag tree2 = new Tag(path, XmlNs.html, "div");

        final TestChangesContext cp = new TestChangesContext();
        new Diff(Optional.of(tree1), tree2,  cp).run();
        assertEquals("-TAG:0:1 +TAG:1:div", cp.resultAsString());
    }

    @Test
    public void should_create_tags_for_added_children() {
        final Tag tree1 = new Tag(path, XmlNs.html, "div");

        final Tag tree2 = new Tag(path, XmlNs.html, "div");
        tree2.addChild(new Tag(path.incLevel(), XmlNs.html, "span"));
        tree2.addChild(new Tag(path.incLevel(), XmlNs.html, "span"));

        final TestChangesContext cp = new TestChangesContext();
        new Diff(Optional.of(tree1), tree2,  cp).run();
        assertEquals("+TAG:1_1:span +TAG:1_2:span", cp.resultAsString());
    }

    @Test
    public void should_remove_and_add_for_replaced_tag() {
        final Tag tree1 = new Tag(path, XmlNs.html, "div");
        tree1.addChild(new Tag(path.incLevel(), XmlNs.html, "span"));

        final Tag tree2 = new Tag(path, XmlNs.html, "div");
        tree2.addChild(new Tag(path.incLevel(), XmlNs.html, "a"));

        final TestChangesContext cp = new TestChangesContext();
        new Diff(Optional.of(tree1), tree2,  cp).run();
        assertEquals("-TAG:1:1_1 +TAG:1_1:a", cp.resultAsString());
    }

    @Test
    public void should_remove_and_add_for_replaced_tag_with_children() {
        final Tag tree1 = new Tag(path, XmlNs.html, "body");

        final Tag tree2 = new Tag(path, XmlNs.html, "div");
        final Tag child21 = new Tag(path.incLevel(), XmlNs.html, "a");
        child21.addChild(new Tag(path.incLevel().incLevel(), XmlNs.html, "canvas"));
        child21.addChild(new Tag(path.incLevel().incLevel().incSibling(), XmlNs.html, "span"));
        tree2.addChild(child21);

        final TestChangesContext cp = new TestChangesContext();
        new Diff(Optional.of(tree1), tree2,  cp).run();
        assertEquals("-TAG:0:1 +TAG:1:div +TAG:1_1:a +TAG:1_1_1:canvas +TAG:1_1_2:span", cp.resultAsString());
    }

    @Test
    public void should_add_for_a_new_child() {
        final Tag ul1 = new Tag(path, XmlNs.html, "ul");
        final Tag li11 = new Tag(path.incLevel(), XmlNs.html, "li");
        li11.addChild(new Text(li11.path().incLevel(), "first"));
        ul1.addChild(li11);
        final Tag li12 = new Tag(path.incLevel(), XmlNs.html, "li");
        li12.addChild(new Text(li12.path().incLevel(), "second"));
        ul1.addChild(li12);

        final Tag ul2 = new Tag(path, XmlNs.html, "ul");
        final Tag li21 = new Tag(path.incLevel(), XmlNs.html, "li");
        li21.addChild(new Text(li21.path().incLevel(), "first"));
        ul2.addChild(li21);
        final Tag li22 = new Tag(path.incLevel(), XmlNs.html, "li");
        li22.addChild(new Text(li22.path().incLevel(), "second"));
        ul2.addChild(li22);
        final Tag li23 = new Tag(path.incLevel(), XmlNs.html, "li");
        li23.addChild(new Text(li23.path().incLevel(), "third"));
        ul2.addChild(li23);

        final TestChangesContext cp = new TestChangesContext();
        new Diff(Optional.of(ul1), ul2,  cp).run();
        assertEquals("+TAG:1_3:li+TEXT:1_3:1_3_1=third", cp.resultAsString());
    }


    @Test
    public void should_add_attribute() {
        final Tag tree1 = new Tag(path, XmlNs.html, "div");

        final Tag tree2 = new Tag(path, XmlNs.html, "div");
        tree2.addAttribute("attr1", "value1", true);

        final TestChangesContext cp = new TestChangesContext();
        new Diff(Optional.of(tree1), tree2,  cp).run();
        assertEquals("+ATTR:1:attr1=value1:true", cp.resultAsString());
    }

    @Test
    public void should_remove_attribute() {
        final Tag tree1 = new Tag(path, XmlNs.html, "div");
        tree1.addAttribute("attr1", "value1", true);

        final Tag tree2 = new Tag(path, XmlNs.html, "div");

        final TestChangesContext cp = new TestChangesContext();
        new Diff(Optional.of(tree1), tree2,  cp).run();
        assertEquals("-ATTR:1:attr1", cp.resultAsString());
    }

    @Test
    public void should_add_style() {
        final Tag tree1 = new Tag(path, XmlNs.html, "div");

        final Tag tree2 = new Tag(path, XmlNs.html, "div");
        tree2.addStyle("style1", "value1");

        final TestChangesContext cp = new TestChangesContext();
        new Diff(Optional.of(tree1), tree2,  cp).run();
        assertEquals("+STYLE:1:style1=value1", cp.resultAsString());
    }

    @Test
    public void should_remove_style() {
        final Tag tree1 = new Tag(path, XmlNs.html, "div");
        tree1.addStyle("style1", "value1");

        final Tag tree2 = new Tag(path, XmlNs.html, "div");

        final TestChangesContext cp = new TestChangesContext();
        new Diff(Optional.of(tree1), tree2,  cp).run();
        assertEquals("-STYLE:1:style1", cp.resultAsString());
    }

    static class TestChangesContext implements DomChangesContext {
        final StringBuilder sb = new StringBuilder();

        public String resultAsString() {
            return sb.toString().trim();
        }

        @Override
        public void remove(final VirtualDomPath parentId, final VirtualDomPath id) {
            insertDelimiter(sb);
            sb.append("-TAG:" + parentId + ":" + id);
        }

        @Override
        public void create(final VirtualDomPath id, final XmlNs xmlNs, final String tag) {
            insertDelimiter(sb);
            sb.append("+TAG:" + id + ":" + tag);
        }

        @Override
        public void removeAttr(final VirtualDomPath id, final XmlNs xmlNs, final String name, final boolean isProperty) {
            insertDelimiter(sb);
            sb.append("-ATTR:" + id + ":" + name);
        }

        @Override
        public void setAttr(final VirtualDomPath id, final XmlNs xmlNs, final String name, final String value, final boolean isProperty) {
            insertDelimiter(sb);
            sb.append("+ATTR:" + id + ":" + name + "=" + value + ":" + isProperty);
        }

        @Override
        public void removeStyle(final VirtualDomPath id, final String name) {
            insertDelimiter(sb);
            sb.append("-STYLE:" + id + ":" + name);
        }

        @Override
        public void setStyle(final VirtualDomPath id, final String name, final String value) {
            sb.append("+STYLE:" + id + ":" + name + "=" + value);
            insertDelimiter(sb);
        }

        @Override
        public void createText(final VirtualDomPath parenPath, final VirtualDomPath path, final String text) {
            sb.append("+TEXT:" + parenPath + ":" + path + "=" + text);
            insertDelimiter(sb);
        }

        private void insertDelimiter(final StringBuilder sb) {
            if (sb.length() != 0) sb.append(" ");
        }
    }
}
