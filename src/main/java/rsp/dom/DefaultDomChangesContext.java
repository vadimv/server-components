package rsp.dom;

import java.util.*;

public final class DefaultDomChangesContext implements DomChangesContext {
    public final Set<TreePositionPath> elementsToRemove = new HashSet<>();
    public final List<DomChange> commands = new ArrayList<>();

    @Override
    public void removeAttr(final TreePositionPath path, final String name, final boolean isProperty) {
        commands.add(new RemoveAttr(path, name, isProperty));
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
    public void setAttr(final TreePositionPath path, final String name, final String value, final boolean isProperty) {
        commands.add(new SetAttr(path, name, value, isProperty));
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
    public void createTag(final TreePositionPath path, final String tag) {
        commands.add(new Create(path, tag));
    }

    public interface DomChange {}

    public static final class RemoveAttr implements DomChange {
        public final TreePositionPath path;
        public final String name;
        public final boolean isProperty;

        public RemoveAttr(final TreePositionPath path, final String name, final boolean isProperty) {
            this.path = path;
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
                    Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, name, isProperty);
        }

        @Override
        public String toString() {
            return "RemoveAttr{" +
                    "componentPath=" + path +
                    ", name='" + name + '\'' +
                    ", isProperty=" + isProperty +
                    '}';
        }
    }

    public static final class RemoveStyle implements DomChange {
        public final TreePositionPath path;
        public final String name;
        public RemoveStyle(final TreePositionPath path, final String name) {
            this.path = path;
            this.name = name;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final RemoveStyle that = (RemoveStyle) o;
            return Objects.equals(path, that.path) &&
                    Objects.equals(name, that.name);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, name);
        }

        @Override
        public String toString() {
            return "RemoveStyle{" +
                    "componentPath=" + path +
                    ", name='" + name + '\'' +
                    '}';
        }
    }

    public static final class Remove implements DomChange {
        public final TreePositionPath parentPath;
        public final TreePositionPath path;
        public Remove(final TreePositionPath parentPath, final TreePositionPath path) {
            this.parentPath = parentPath;
            this.path = path;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Remove remove = (Remove) o;
            return Objects.equals(parentPath, remove.parentPath) &&
                    Objects.equals(path, remove.path);
        }

        @Override
        public int hashCode() {
            return Objects.hash(parentPath, path);
        }

        @Override
        public String toString() {
            return "Remove{" +
                    "parentPath=" + parentPath +
                    ", componentPath=" + path +
                    '}';
        }
    }

    public static final class SetAttr implements DomChange {
        public final TreePositionPath path;
        public final String name;
        public final String value;
        public final boolean isProperty;

        public SetAttr(final TreePositionPath path, final String name, final String value, final boolean isProperty) {
            this.path = path;
            this.name = name;
            this.value = value;
            this.isProperty = isProperty;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final SetAttr setAttr = (SetAttr) o;
            return isProperty == setAttr.isProperty &&
                    Objects.equals(path, setAttr.path) &&
                    Objects.equals(name, setAttr.name) &&
                    Objects.equals(value, setAttr.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, name, value, isProperty);
        }

        @Override
        public String toString() {
            return "SetAttr{" +
                    "componentPath=" + path +
                    ", name='" + name + '\'' +
                    ", value='" + value + '\'' +
                    ", isProperty=" + isProperty +
                    '}';
        }
    }

    public static final class SetStyle implements DomChange {
        public final TreePositionPath path;
        public final String name;
        public final String value;
        public SetStyle(final TreePositionPath path, final String name, final String value) {
            this.path = path;
            this.name = name;
            this.value = value;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final SetStyle setStyle = (SetStyle) o;
            return Objects.equals(path, setStyle.path) &&
                    Objects.equals(name, setStyle.name) &&
                    Objects.equals(value, setStyle.value);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, name, value);
        }

        @Override
        public String toString() {
            return "SetStyle{" +
                    "componentPath=" + path +
                    ", name='" + name + '\'' +
                    ", value='" + value + '\'' +
                    '}';
        }
    }

    public static final class CreateText implements DomChange {
        public final TreePositionPath parentPath;
        public final TreePositionPath path;
        public final String text;
        public CreateText(final TreePositionPath parentPath, final TreePositionPath path, final String text) {
            this.parentPath = parentPath;
            this.path = path;
            this.text = text;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final CreateText that = (CreateText) o;
            return Objects.equals(parentPath, that.parentPath) &&
                    Objects.equals(path, that.path) &&
                    Objects.equals(text, that.text);
        }

        @Override
        public int hashCode() {
            return Objects.hash(parentPath, path, text);
        }

        @Override
        public String toString() {
            return "CreateText{" +
                    "parentPath=" + parentPath +
                    ", componentPath=" + path +
                    ", text='" + text + '\'' +
                    '}';
        }
    }

    public static final class Create implements DomChange {
        public final TreePositionPath path;
        public final String tag;
        public Create(final TreePositionPath path, final String tag) {
            this.path = path;
            this.tag = tag;
        }

        @Override
        public boolean equals(final Object o) {
            if (this == o) return true;
            if (o == null || getClass() != o.getClass()) return false;
            final Create create = (Create) o;
            return Objects.equals(path, create.path) &&
                    Objects.equals(tag, create.tag);
        }

        @Override
        public int hashCode() {
            return Objects.hash(path, tag);
        }

        @Override
        public String toString() {
            return "Create{" +
                    "componentPath=" + path +
                    ", tag='" + tag + '\'' +
                    '}';
        }
    }
}
