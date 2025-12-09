package rsp.dom;

import java.util.*;

/**
 * Collects information about a DOM trees differences as a sequence of DomChange objects and remembering positions of nodes to be removed.
 */
public final class DefaultDomChangesContext implements DomChangesContext {
    public final Set<TreePositionPath> elementsToRemove = new HashSet<>();
    public final List<DomChange> changes = new ArrayList<>();

    @Override
    public void removeAttr(final TreePositionPath path, final XmlNs xmlNs, final String name, final boolean isProperty) {
        changes.add(new RemoveAttr(path, xmlNs, name, isProperty));
    }

    @Override
    public void removeStyle(final TreePositionPath path, final String name) {
        changes.add(new RemoveStyle(path, name));
    }

    @Override
    public void removeNode(final TreePositionPath parentPath, final TreePositionPath path) {
        changes.add(new Remove(parentPath, path));
        elementsToRemove.add(path);
    }

    @Override
    public void setAttr(final TreePositionPath path, final XmlNs xmlNs, final String name, final String value, final boolean isProperty) {
        changes.add(new SetAttr(path, xmlNs, name, value, isProperty));
    }

    @Override
    public void setStyle(final TreePositionPath path, final String name, final String value) {
        changes.add(new SetStyle(path, name, value));
    }

    @Override
    public void createText(final TreePositionPath parentPath, final TreePositionPath path, final String text) {
        changes.add(new CreateText(parentPath, path, text));
    }

    @Override
    public void createTag(final TreePositionPath path, final XmlNs xmlNs, final String tag) {
        changes.add(new Create(path, xmlNs, tag));
    }

    /**
     * Represents a unit of information about a DOM tree change.
     * Instances of the subtypes of this interface are generated during a comparison of an DOM tree before and after an update
     * and can be used for sending commands to request synchronized changes on another 'mirrored' DOM tree, e.g. on the client-side.
     */
    public sealed interface DomChange {}

    public record RemoveAttr(TreePositionPath path, XmlNs xmlNs, String name, boolean isProperty) implements DomChange {
    }

    public record RemoveStyle(TreePositionPath path, String name) implements DomChange {
    }

    public record Remove(TreePositionPath parentPath, TreePositionPath path) implements DomChange {
    }

    public record SetAttr(TreePositionPath path, XmlNs xmlNs, String name, String value, boolean isProperty) implements DomChange {
    }

    public record SetStyle(TreePositionPath path, String name, String value) implements DomChange {
    }

    public record CreateText(TreePositionPath parentPath, TreePositionPath path, String text) implements DomChange {
    }

    public record Create(TreePositionPath path, XmlNs xmlNs, String tag) implements DomChange {
    }
}
