package rsp.dom;

import java.util.*;

/**
 * Collects information about a DOM trees differences as a sequence of DomChange objects and remembering positions of nodes to be removed.
 */
public final class DefaultDomChangesContext implements DomChangesContext {
    public final Set<NodeId> elementsToRemove = new HashSet<>();
    public final Set<NodeId> elementsToCreate = new HashSet<>();
    public final List<DomChange> changes = new ArrayList<>();

    @Override
    public void removeAttr(final NodeId path, final XmlNs xmlNs, final String name, final boolean isProperty) {
        changes.add(new RemoveAttr(path, xmlNs, name, isProperty));
    }

    @Override
    public void removeNode(final NodeId parentPath, final NodeId path) {
        changes.add(new Remove(parentPath, path));
        elementsToRemove.add(path);
    }

    @Override
    public void setAttr(final NodeId path, final XmlNs xmlNs, final String name, final String value, final boolean isProperty) {
        changes.add(new SetAttr(path, xmlNs, name, value, isProperty));
    }

    @Override
    public void createText(final NodeId parentPath, final NodeId path, final String text) {
        changes.add(new CreateText(parentPath, path, text));
        elementsToCreate.add(path);
    }

    @Override
    public void createTag(final NodeId path, final XmlNs xmlNs, final String tag) {
        changes.add(new Create(path, xmlNs, tag));
        elementsToCreate.add(path);
    }

    @Override
    public void insertBefore(final NodeId parentPath, final NodeId path, final NodeId beforePath) {
        changes.add(new InsertBefore(parentPath, path, beforePath));
    }

    /**
     * Represents a unit of information about a DOM tree change.
     * Instances of the subtypes of this interface are generated during a comparison of an DOM tree before and after an update
     * and can be used for sending commands to request synchronized changes on another 'mirrored' DOM tree, e.g. on the client-side.
     */
    public sealed interface DomChange {}

    public record RemoveAttr(NodeId path, XmlNs xmlNs, String name, boolean isProperty) implements DomChange {
        public RemoveAttr {
            Objects.requireNonNull(path);
            Objects.requireNonNull(xmlNs);
            Objects.requireNonNull(name);
        }
    }

    public record RemoveStyle(TreePositionPath path, String name) implements DomChange {
        public RemoveStyle {
            Objects.requireNonNull(path);
            Objects.requireNonNull(name);
        }
    }

    public record Remove(NodeId parentPath, NodeId path) implements DomChange {
        public Remove {
            Objects.requireNonNull(parentPath);
            Objects.requireNonNull(path);
        }
    }

    public record SetAttr(NodeId path, XmlNs xmlNs, String name, String value, boolean isProperty) implements DomChange {
        public SetAttr {
            Objects.requireNonNull(path);
            Objects.requireNonNull(xmlNs);
            Objects.requireNonNull(name);
            Objects.requireNonNull(value);
        }
    }

    public record SetStyle(TreePositionPath path, String name, String value) implements DomChange {
        public SetStyle {
            Objects.requireNonNull(path);
            Objects.requireNonNull(name);
            Objects.requireNonNull(value);
        }
    }

    public record CreateText(NodeId parentPath, NodeId path, String text) implements DomChange {
        public CreateText {
            Objects.requireNonNull(parentPath);
            Objects.requireNonNull(path);
            Objects.requireNonNull(text);
        }
    }

    public record Create(NodeId path, XmlNs xmlNs, String tag) implements DomChange {
        public Create {
            Objects.requireNonNull(path);
            Objects.requireNonNull(xmlNs);
            Objects.requireNonNull(tag);
        }
    }

    /**
     * Relocates an existing child before {@code beforePath}, or appends it when {@code beforePath} is null.
     */
    public record InsertBefore(NodeId parentPath, NodeId path, NodeId beforePath) implements DomChange {
        public InsertBefore {
            Objects.requireNonNull(parentPath);
            Objects.requireNonNull(path);
        }
    }
}
