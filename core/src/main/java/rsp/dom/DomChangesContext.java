package rsp.dom;

/**
 * Represents an abstraction for gathering information about changes in a DOM tree, for example when comparing trees before and after an update.
 */
public interface DomChangesContext {

    /**
     * Removes an attribute from a node.
     * @param path the path to the node, must not be null
     * @param xmlNs the XML namespace, must not be null
     * @param name the name of the attribute, must not be null
     * @param isProperty true if the attribute is a property, false otherwise
     */
    void removeAttr(TreePositionPath path, XmlNs xmlNs, String name, boolean isProperty);

    /**
     * Removes a style from a node.
     * @param path the path to the node, must not be null
     * @param name the name of the style, must not be null
     */
    void removeStyle(TreePositionPath path, String name);

    /**
     * Removes a node.
     * @param parentPath the path to the parent node, must not be null
     * @param path the path to the node to remove, must not be null
     */
    void removeNode(TreePositionPath parentPath, TreePositionPath path);

    /**
     * Sets an attribute on a node.
     * @param path the path to the node, must not be null
     * @param xmlNs the XML namespace, must not be null
     * @param name the name of the attribute, must not be null
     * @param value the value of the attribute, must not be null
     * @param isProperty true if the attribute is a property, false otherwise
     */
    void setAttr(TreePositionPath path, XmlNs xmlNs, String name, String value, boolean isProperty);

    /**
     * Sets a style on a node.
     * @param path the path to the node, must not be null
     * @param name the name of the style, must not be null
     * @param value the value of the style, must not be null
     */
    void setStyle(TreePositionPath path, String name, String value);

    /**
     * Creates a new tag.
     * @param path the path to the new tag, must not be null
     * @param xmlNs the XML namespace, must not be null
     * @param tag the name of the tag, must not be null
     */
    void createTag(TreePositionPath path, XmlNs xmlNs, String tag);

    /**
     * Creates a new text node.
     * @param parentPath the path to the parent node, must not be null
     * @param path the path to the new text node, must not be null
     * @param text the text content, must not be null
     */
    void createText(TreePositionPath parentPath, TreePositionPath path, String text);
}
