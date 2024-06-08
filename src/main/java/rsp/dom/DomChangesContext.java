package rsp.dom;

public interface DomChangesContext {
    void removeAttr(TreePositionPath path, String name, boolean isProperty);
    void removeStyle(TreePositionPath path, String name);
    void removeNode(TreePositionPath parentPath, TreePositionPath path);
    void setAttr(TreePositionPath path, String name, String value, boolean isProperty);
    void setStyle(TreePositionPath path, String name, String value);
    void createTag(TreePositionPath path, String tag);
    void createText(TreePositionPath parentPath, TreePositionPath path, String text);
}
