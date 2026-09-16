package rsp.dom;

/**
 * Represents an abstraction for gathering information about changes in a DOM tree, for example when comparing trees before and after an update.
 * Nodes are addressed by {@link NodeId}, which carries either positional or stable key segments.
 */
public interface DomChangesContext {

    /**
     * Removes an attribute from a node.
     * @param path the id of the node, must not be null
     * @param xmlNs the XML namespace, must not be null
     * @param name the name of the attribute, must not be null
     * @param isProperty true if the attribute is a property, false otherwise
     */
    void removeAttr(NodeId path, XmlNs xmlNs, String name, boolean isProperty);

    /**
     * Removes a node.
     * @param parentPath the id of the parent node, must not be null
     * @param path the id of the node to remove, must not be null
     */
    void removeNode(NodeId parentPath, NodeId path);

    /**
     * Sets an attribute on a node.
     * @param path the id of the node, must not be null
     * @param xmlNs the XML namespace, must not be null
     * @param name the name of the attribute, must not be null
     * @param value the value of the attribute, must not be null
     * @param isProperty true if the attribute is a property, false otherwise
     */
    void setAttr(NodeId path, XmlNs xmlNs, String name, String value, boolean isProperty);

    /**
     * Creates a new tag.
     * @param path the id of the new tag, must not be null
     * @param xmlNs the XML namespace, must not be null
     * @param tag the name of the tag, must not be null
     */
    void createTag(NodeId path, XmlNs xmlNs, String tag);

    /**
     * Creates a new text node.
     * @param parentPath the id of the parent node, must not be null
     * @param path the id of the new text node, must not be null
     * @param text the text content, must not be null
     */
    void createText(NodeId parentPath, NodeId path, String text);

    /**
     * Relocates an existing child node so that it precedes the reference sibling, or appends it
     * to the end of the parent when {@code beforePath} is null. Used by keyed list diffing to
     * move a node instead of rewriting it.
     * @param parentPath the id of the parent node, must not be null
     * @param path the id of the node to move, must not be null
     * @param beforePath the id of the sibling to insert before, or null to append at the end
     */
    void insertBefore(NodeId parentPath, NodeId path, NodeId beforePath);
}
