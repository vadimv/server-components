package rsp.dom;

public interface DomChangesContext {
    void removeAttr(TreePositionPath path, XmlNs xmlNs, String name, boolean isProperty);
    void removeStyle(TreePositionPath path, String name);
    void remove(TreePositionPath parentPath, TreePositionPath path);
    void setAttr(TreePositionPath path, XmlNs xmlNs, String name, String value, boolean isProperty);
    void setStyle(TreePositionPath path, String name, String value);
    void createText(TreePositionPath parentPath, TreePositionPath path, String text);
    void create(TreePositionPath path, XmlNs xmlNs, String tag);
}
