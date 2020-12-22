package rsp.dom;

public interface ChangesPerformer {
    void removeAttr(VirtualDomPath id, XmlNs xmlNs, String name, boolean isProperty);
    void removeStyle(VirtualDomPath id, String name);
    void remove(VirtualDomPath parentId, VirtualDomPath id);
    void setAttr(VirtualDomPath id, XmlNs xmlNs, String name, String value, boolean isProperty);
    void setStyle(VirtualDomPath id, String name, String value);
    void createText(VirtualDomPath parentId, VirtualDomPath id, String text);
    void create(VirtualDomPath id, XmlNs xmlNs, String tag);
}
