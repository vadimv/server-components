package rsp.dom;

import org.junit.Assert;
import org.junit.Test;

import java.util.Optional;

public class DiffTests {
    final VirtualDomPath path = new VirtualDomPath(1);

    @Test
    public void should_be_empty_diff_for_same_single_tags() {
        Tag tree1 = new Tag(path, XmlNs.html, "html");
        Tag tree2 = new Tag(path, XmlNs.html, "html");
        final TestChangesPerformer cp = new TestChangesPerformer();
        final Diff diff = new Diff(Optional.of(tree1), tree2, cp);
        diff.run();
        Assert.assertEquals("", cp.resultAsString());
    }

    @Test
    public void should_be_remove_and_create_diff_for_different_single_tags() {
        Tag tree1 = new Tag(path, XmlNs.html, "html");
        Tag tree2 = new Tag(path, XmlNs.html, "div");
        final TestChangesPerformer cp = new TestChangesPerformer();
        final Diff diff = new Diff(Optional.of(tree1), tree2,  cp);
        diff.run();
        Assert.assertEquals("-TAG:0:1 +TAG:1:div", cp.resultAsString());
    }

    @Test
    public void should_be_remove_and_create_diff_for_different_tags() {
        Tag tree1 = new Tag(path, XmlNs.html, "div");
        Tag tree2 = new Tag(path, XmlNs.html, "div");
        tree2.addChild(new Tag(path.incLevel(), XmlNs.html, "span"));
        tree2.addChild(new Tag(path.incLevel(), XmlNs.html, "span"));
        final TestChangesPerformer cp = new TestChangesPerformer();
        final Diff diff = new Diff(Optional.of(tree1), tree2,  cp);
        diff.run();
        Assert.assertEquals("+TAG:1_1:span +TAG:1_2:span", cp.resultAsString());

    }

    @Test
    public void should_be_remove_diff_for_different_tags1() {
        Tag tree1 = new Tag(path, XmlNs.html, "div");
        tree1.addChild(new Tag(path.incLevel(), XmlNs.html, "span"));

        Tag tree2 = new Tag(path, XmlNs.html, "div");
        tree2.addChild(new Tag(path.incLevel(), XmlNs.html, "a"));
        final TestChangesPerformer cp = new TestChangesPerformer();
        final Diff diff = new Diff(Optional.of(tree1), tree2,  cp);
        diff.run();
        Assert.assertEquals("-TAG:1:1_1 +TAG:1_1:a", cp.resultAsString());
    }

    @Test
    public void should_be_remove_diff_for_different_tags2() {
        Tag tree1 = new Tag(path, XmlNs.html, "body");

        Tag tree2 = new Tag(path, XmlNs.html, "div");
        Tag child21 = new Tag(path.incLevel(), XmlNs.html, "a");
        child21.addChild(new Tag(path.incLevel().incLevel(), XmlNs.html, "canvas"));
        child21.addChild(new Tag(path.incLevel().incLevel().incSibling(), XmlNs.html, "span"));
        tree2.addChild(child21);
        final TestChangesPerformer cp = new TestChangesPerformer();
        final Diff diff = new Diff(Optional.of(tree1), tree2,  cp);
        diff.run();
        Assert.assertEquals("-TAG:0:1 +TAG:1:div +TAG:1_1:a +TAG:1_1_1:canvas +TAG:1_1_2:span", cp.resultAsString());
    }


    @Test
    public void should_be_remove_diff_for_different_attributes() {
        Tag tree1 = new Tag(path, XmlNs.html, "div");

        Tag tree2 = new Tag(path, XmlNs.html, "div");
        tree2.addAttribute("attr1", "value1", true);
        final TestChangesPerformer cp = new TestChangesPerformer();
        final Diff diff = new Diff(Optional.of(tree1), tree2,  cp);
        diff.run();
        Assert.assertEquals("+ATTR:1:attr1=value1", cp.resultAsString());
    }

    static class TestChangesPerformer implements DomChangesPerformer {
        final StringBuilder sb = new StringBuilder();

        public String resultAsString() {
            return sb.toString().trim();
        }

        @Override
        public void remove(VirtualDomPath parentId, VirtualDomPath id) {
            insertDelimiter(sb);
            sb.append("-TAG:" + parentId + ":" + id);
        }

        @Override
        public void create(VirtualDomPath id, XmlNs xmlNs, String tag) {
            insertDelimiter(sb);
            sb.append("+TAG:" + id + ":" + tag);
        }

        @Override
        public void removeAttr(VirtualDomPath id, XmlNs xmlNs, String name,  boolean isProperty) {
            insertDelimiter(sb);
            sb.append("-ATTR:" + id);
        }

        @Override
        public void setAttr(VirtualDomPath id, XmlNs xmlNs, String name, String value, boolean isProperty) {
            insertDelimiter(sb);
            sb.append("+ATTR:" + id + ":" + name + "=" + value);
        }

        @Override
        public void removeStyle(VirtualDomPath id, String name) {
            insertDelimiter(sb);
            sb.append("-STYLE:" + id + ":" + name);
        }

        @Override
        public void setStyle(VirtualDomPath id, String name, String value) {
            sb.append("+STYLE:" + id + ":" + name + "=" + value);
            insertDelimiter(sb);
        }

        @Override
        public void createText(VirtualDomPath parenPath, VirtualDomPath path, String text) {
            sb.append("+TEXT:" + parenPath + ":" + path + "=" + text);
            insertDelimiter(sb);
        }

        private void insertDelimiter(StringBuilder sb) {
            if (sb.length() != 0) sb.append(" ");
        }
    }
}
