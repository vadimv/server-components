package rsp.dom;

import rsp.ChangesPerformer;
import rsp.XmlNs;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class RemoteDomChangesPerformer implements ChangesPerformer {
    private static final int  CREATE = 0; // (id, childId, xmlNs, tag)
    private static final int  CREATE_TEXT = 1; // (id, childId, text)
    private static final int  REMOVE = 2; // (id, childId)
    private static final int  SET_ATTR = 3; // (id, xmlNs, name, value, isProperty)
    private static final int  REMOVE_ATTR = 4; // (id, xmlNs, name, isProperty)
    private static final int  SET_STYLE = 5; // (id, name, value)
    private static final int  REMOVE_STYLE = 6; // (id, name)
    
    public final List<String> commands = new ArrayList<>();

    @Override
    public void removeAttr(Path id, XmlNs xmlNs, String name) {
        commands.add("4," + id + ",\"" + xmlNs + "\",\"" + name + "\"," + "false");
    }

    @Override
    public void removeStyle(Path id, String name) {
        commands.add("6,\"" + id + "\",\"" + name + "\"");
    }

    @Override
    public void remove(Path id) {
        commands.add("2,\"" + id + "\",\"" + id.parent().get() + "\"");
    }

    @Override
    public void setAttr(Path id, XmlNs xmlNs, String name, String value) {
        commands.add("3," + id + ",\"" + xmlNs + "\",\"" + name + "\"," + value + ",false");
    }

    @Override
    public void setStyle(Path id, String name, String value) {
        commands.add("5,\"" + id + "\",\"" + name + "\",\"" + value + "\",false");
    }

    @Override
    public void createText(Path parentId, Path id, String text) {
        commands.add("1,\"" + parentId + "\",\"" + id + "\",\"" + text + "\"");
    }

    @Override
    public void create(Path id, XmlNs xmlNs, String tag) {
        commands.add("0,\"" + id + "\"," +  xmlNs + ",\"" + tag + "\"");
    }

    public Optional<String> commandsString() {
        return commands.size() > 0 ? Optional.of("[4," + String.join(",", commands) + "]") : Optional.empty();
    }
}
