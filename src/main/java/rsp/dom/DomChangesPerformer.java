package rsp.dom;

public interface DomChangesPerformer {
    void removeAttr(Id id, XmlNs xmlNs, String name);
    void removeStyle(Id id, String name);
    void remove(Id id);
    void setAttr(Id id, XmlNs xmlNs, String name, String value);
    void setStyle(Id id, String name, String value);
    void createText(Id id, String text);
    void create(Id id, XmlNs xmlNs, String tag);
}
