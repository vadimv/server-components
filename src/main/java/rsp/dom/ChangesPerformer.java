package rsp.dom;

import rsp.dom.Path;
import rsp.dom.XmlNs;

public interface ChangesPerformer {
    void removeAttr(Path id, XmlNs xmlNs, String name);
    void removeStyle(Path id, String name);
    void remove(Path parentId, Path id);
    void setAttr(Path id,  XmlNs xmlNs, String name, String value, boolean isProperty);
    void setStyle(Path id, String name, String value);
    void createText(Path parentId, Path id, String text);
    void create(Path id, XmlNs xmlNs, String tag);
}
