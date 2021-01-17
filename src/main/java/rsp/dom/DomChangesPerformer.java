package rsp.dom;

public interface DomChangesPerformer {
    void removeAttr(VirtualDomPath path, XmlNs xmlNs, String name, boolean isProperty);
    void removeStyle(VirtualDomPath path, String name);
    void remove(VirtualDomPath parentPath, VirtualDomPath path);
    void setAttr(VirtualDomPath path, XmlNs xmlNs, String name, String value, boolean isProperty);
    void setStyle(VirtualDomPath path, String name, String value);
    void createText(VirtualDomPath parentPath, VirtualDomPath path, String text);
    void create(VirtualDomPath path, XmlNs xmlNs, String tag);
}
