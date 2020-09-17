package rsp.dom;

import rsp.ChangesPerformer;
import rsp.XmlNs;

import java.util.ArrayList;
import java.util.List;

public class RemoteDomChangesPerformer implements ChangesPerformer {
    public final List<DomChange> commands = new ArrayList<>();

    @Override
    public void removeAttr(Path path, XmlNs xmlNs, String name) {
        commands.add(new RemoveAttr(path, xmlNs, name));
    }

    @Override
    public void removeStyle(Path path, String name) {
        commands.add(new RemoveStyle(path, name));
    }

    @Override
    public void remove(Path path) {
        commands.add(new Remove(path));
    }

    @Override
    public void setAttr(Path path, XmlNs xmlNs, String name, String value) {
        commands.add(new SetAttr(path, xmlNs, name, value, false));
    }

    @Override
    public void setStyle(Path path, String name, String value) {
        commands.add(new SetStyle(path, name, value));
    }

    @Override
    public void createText(Path parentPath, Path path, String text) {
        commands.add(new CreateText(parentPath, path, text));
    }

    @Override
    public void create(Path path, XmlNs xmlNs, String tag) {
        commands.add(new Create(path, xmlNs, tag));
    }

    public interface DomChange {}

    public static final class RemoveAttr implements DomChange {
        public final Path path;
        public final XmlNs xmlNs;
        public final String name;
        public RemoveAttr(Path path, XmlNs xmlNs, String name) {
            this.path = path;
            this.xmlNs = xmlNs;
            this.name = name;
        }
    }

    public static final class RemoveStyle implements DomChange {
        public final Path path;
        public final String name;
        public RemoveStyle(Path path, String name) {
            this.path = path;
            this.name = name;
        }
    }

    public static class Remove implements DomChange {
        public final Path path;
        public Remove(Path path) {
            this.path = path;
        }
    }

    public static class SetAttr implements DomChange {
        public final Path path;
        public final XmlNs xmlNs;
        public final String name;
        public final String value;
        public final boolean isProperty;
        public SetAttr(Path path, XmlNs xmlNs, String name, String value, boolean isProperty) {
            this.path = path;
            this.xmlNs = xmlNs;
            this.name = name;
            this.value = value;
            this.isProperty = isProperty;
        }
    }

    public static class SetStyle implements DomChange {
        public final Path path;
        public final String name;
        public final String value;
        public SetStyle(Path path, String name, String value) {
            this.path = path;
            this.name = name;
            this.value = value;
        }
    }

    public static class CreateText implements DomChange {
        public final Path parentPath;
        public final Path path;
        public final String text;
        public CreateText(Path parentPath, Path path, String text) {
            this.parentPath = parentPath;
            this.path = path;
            this.text = text;
        }
    }

    public static class Create implements DomChange {
        public final Path path;
        public final XmlNs xmlNs;
        public final String tag;
        public Create(Path path, XmlNs xmlNs, String tag) {
            this.path = path;
            this.xmlNs = xmlNs;
            this.tag = tag;
        }
    }
}
