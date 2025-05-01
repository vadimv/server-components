package rsp.dom;

import java.util.*;

public final class DefaultDomChangesContext implements DomChangesContext {
    public final Set<TreePositionPath> elementsToRemove = new HashSet<>();
    public final List<DomChange> commands = new ArrayList<>();

    @Override
    public void removeAttr(final TreePositionPath path, final XmlNs xmlNs, final String name, final boolean isProperty) {
        commands.add(new RemoveAttr(path, xmlNs, name, isProperty));
    }

    @Override
    public void removeStyle(final TreePositionPath path, final String name) {
        commands.add(new RemoveStyle(path, name));
    }

    @Override
    public void removeNode(final TreePositionPath parentPath, final TreePositionPath path) {
        commands.add(new Remove(parentPath, path));
        elementsToRemove.add(path);
    }

    @Override
    public void setAttr(final TreePositionPath path, final XmlNs xmlNs, final String name, final String value, final boolean isProperty) {
        commands.add(new SetAttr(path, xmlNs, name, value, isProperty));
    }

    @Override
    public void setStyle(final TreePositionPath path, final String name, final String value) {
        commands.add(new SetStyle(path, name, value));
    }

    @Override
    public void createText(final TreePositionPath parentPath, final TreePositionPath path, final String text) {
        commands.add(new CreateText(parentPath, path, text));
    }

    @Override
    public void createTag(final TreePositionPath path, final XmlNs xmlNs, final String tag) {
        commands.add(new Create(path, xmlNs, tag));
    }

    public interface DomChange {}

    public static final class RemoveAttr implements DomChange {
        public final TreePositionPath path;
        public final XmlNs xmlNs;
        public final String name;
        public final boolean isProperty;

        public RemoveAttr(final TreePositionPath path, final XmlNs xmlNs, final String name, final boolean isProperty) {
            this.path = path;
            this.xmlNs = xmlNs;
            this.name = name;
            this.isProperty = isProperty;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final RemoveAttr that = (RemoveAttr) o;
            return isProperty == that.isProperty &&
                    Objects.equals(path, that.path) &&
                    Objects.equals(xmlNs, that.xmlNs) &&
                    Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, xmlNs, name, isProperty);
        }

        @Override
        public String toString() {
            return "RemoveAttr{" +
                    "componentPath=" + path +
                    ", xmlNs=" + xmlNs +
                    ", name='" + name + '\'' +
                    ", isProperty=" + isProperty +
                    '}';
        }
    }

    public record RemoveStyle(TreePositionPath path, String name) implements DomChange {

        @Override
        public String toString() {
            return "RemoveStyle{" +
                    "componentPath=" + path +
                    ", name='" + name + '\'' +
                    '}';
        }
        }

    public record Remove(TreePositionPath parentPath, TreePositionPath path) implements DomChange {

        @Override
        public String toString() {
            return "Remove{" +
                    "parentPath=" + parentPath +
                    ", componentPath=" + path +
                    '}';
        }
        }

    public record SetAttr(TreePositionPath path, XmlNs xmlNs, String name, String value, boolean isProperty) implements DomChange {

        @Override
        public String toString() {
            return "SetAttr{" +
                    "componentPath=" + path +
                    ", xmlNs=" + xmlNs +
                    ", name='" + name + '\'' +
                    ", value='" + value + '\'' +
                    ", isProperty=" + isProperty +
                    '}';
            }
        }

    public record SetStyle(TreePositionPath path, String name, String value) implements DomChange {

        @Override
        public String toString() {
            return "SetStyle{" +
                    "componentPath=" + path +
                    ", name='" + name + '\'' +
                    ", value='" + value + '\'' +
                    '}';
        }
        }

    public record CreateText(TreePositionPath parentPath, TreePositionPath path, String text) implements DomChange {

        @Override
        public String toString() {
            return "CreateText{" +
                    "parentPath=" + parentPath +
                    ", componentPath=" + path +
                    ", text='" + text + '\'' +
                    '}';
        }
    }

    public record Create(TreePositionPath path, XmlNs xmlNs, String tag) implements DomChange {

        @Override
        public String toString() {
            return "Create{" +
                    "componentPath=" + path +
                    ", xmlNs=" + xmlNs +
                    ", tag='" + tag + '\'' +
                    '}';
        }
    }
}
