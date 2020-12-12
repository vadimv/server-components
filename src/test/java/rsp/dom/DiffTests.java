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
        Assert.assertEquals("-T:1 +T:1,div", cp.resultAsString());
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
        Assert.assertEquals("+T:1_1,span +T:1_2,span", cp.resultAsString());

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
        Assert.assertEquals("-T:1_1 +T:1_1,a", cp.resultAsString());;
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
        Assert.assertEquals("-T:1 +T:1,div +T:1_1,a +T:1_1_1,canvas +T:1_1_2,span", cp.resultAsString());
    }


    @Test
    public void should_be_remove_diff_for_different_attributes() {
        Tag tree1 = new Tag(path, XmlNs.html, "div");

        Tag tree2 = new Tag(path, XmlNs.html, "div");
        tree2.addAttribute("attr1", "value1", true);
        final TestChangesPerformer cp = new TestChangesPerformer();
        final Diff diff = new Diff(Optional.of(tree1), tree2,  cp);
        diff.run();
        Assert.assertEquals("+A:1,attr1,value1", cp.resultAsString());
    }

    static class TestChangesPerformer implements ChangesPerformer {
        final StringBuilder sb = new StringBuilder();

        public String resultAsString() {
            return sb.toString().trim();
        }

        @Override
        public void removeAttr(VirtualDomPath id, XmlNs xmlNs, String name) {
            sb.append("-A:" + id + " ");
        }

        @Override
        public void removeStyle(VirtualDomPath id, String name) {

        }

        @Override
        public void remove(VirtualDomPath parentId, VirtualDomPath id) {
            sb.append("-T:" + parentId + " " + id + " ");
        }

        @Override
        public void setAttr(VirtualDomPath id, XmlNs xmlNs, String name, String value, boolean isProperty) {
            sb.append("+A:" + id + "," + name + "," + value );
        }

        @Override
        public void setStyle(VirtualDomPath id, String name, String value) {

        }

        @Override
        public void createText(VirtualDomPath parentId, VirtualDomPath id, String text) {

        }

        @Override
        public void create(VirtualDomPath id, XmlNs xmlNs, String tag) {
            sb.append("+T:" + id + "," + tag + " ");
        }
    }
}
