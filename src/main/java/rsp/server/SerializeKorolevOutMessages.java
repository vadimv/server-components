package rsp.server;

import rsp.dom.Path;
import rsp.dom.RemoteDomChangesPerformer.*;

import java.util.Arrays;
import java.util.function.Consumer;

public class SerializeKorolevOutMessages implements OutMessages {
    private static final int SET_RENDER_NUM = 0; // (n)
    private static final int CLEAN_ROOT = 1; // ()
    private static final int LISTEN_EVENT = 2; // (type, preventDefault)
    private static final int EXTRACT_PROPERTY = 3; // (descriptor, id, propertyName )
    private static final int MODIFY_DOM = 4; // (commands)
    private static final int FOCUS = 5; // (id) {
    private static final int CHANGE_PAGE_URL = 6; // (path)
    private static final int UPLOAD_FORM = 7; // (id, descriptor)
    private static final int RELOAD_CSS = 8; // ()
    private static final int KEEP_ALIVE = 9; // ()
    private static final int EVAL_JS = 10; // (code)
    private static final int EXTRACT_EVENT_DATA = 11; // (descriptor, renderNum)
    private static final int LIST_FILES = 12; // (id, descriptor)
    private static final int UPLOAD_FILE = 13; // (id, descriptor, fileName)
    private static final int REST_FORM = 14; // (id)

    // MODIFY_DOM commands
    private static final int  CREATE = 0; // (id, childId, xmlNs, tag)
    private static final int  CREATE_TEXT = 1; // (id, childId, text)
    private static final int  REMOVE = 2; // (id, childId)
    private static final int  SET_ATTR = 3; // (id, xmlNs, name, value, isProperty)
    private static final int  REMOVE_ATTR = 4; // (id, xmlNs, name, isProperty)
    private static final int  SET_STYLE = 5; // (id, name, value)
    private static final int  REMOVE_STYLE = 6; // (id, name)
    
    private final Consumer<String> messagesConsumer;

    public SerializeKorolevOutMessages(Consumer<String> messagesConsumer) {
        this.messagesConsumer = messagesConsumer;
    }

    @Override
    public void setRenderNum(int renderNum) {
        final String message = addSquareBrackets(joinString(SET_RENDER_NUM, renderNum));
        messagesConsumer.accept(message);
    }

    @Override
    public void listenEvent(String eventType, boolean b) {
        final String message = addSquareBrackets(joinString(LISTEN_EVENT, quote(eventType), b));
        messagesConsumer.accept(message);
    }

    @Override
    public void extractProperty(Path path, String name, int descriptor) {
        final String message = addSquareBrackets(joinString(EXTRACT_PROPERTY,
                                                            quote(descriptor),
                                                            quote(path),
                                                            quote(name)));
        messagesConsumer.accept(message);
    }

    @Override
    public void modifyDom(DomChange domChange) {
        final String message = addSquareBrackets(joinString(MODIFY_DOM, modifyDomMessageBody(domChange)));
        messagesConsumer.accept(message);
    }

    private String modifyDomMessageBody(DomChange domChange) {
        if(domChange instanceof RemoveAttr) {
            final RemoveAttr c = (RemoveAttr)domChange;
            return joinString(REMOVE_ATTR, quote(c.path), quote(c.xmlNs), quote(c.name), false);
        } else if(domChange instanceof RemoveStyle) {
            final RemoveStyle c = (RemoveStyle)domChange;
            return joinString(REMOVE_STYLE, quote(c.path), quote(c.name), false);
        } else if(domChange instanceof Remove) {
            final Remove c = (Remove)domChange;
            return joinString(REMOVE, quote(c.path));
        } else if(domChange instanceof SetAttr) {
            final SetAttr c = (SetAttr)domChange;
            return joinString(SET_ATTR, quote(c.path), quote(c.xmlNs), quote(c.name), quote(c.value));
        } else if(domChange instanceof SetStyle) {
            final SetStyle c = (SetStyle)domChange;
            return joinString(SET_STYLE, quote(c.path), quote(c.name), quote(c.value));
        } else if(domChange instanceof CreateText) {
            final CreateText c = (CreateText)domChange;
            return joinString(CREATE_TEXT, quote(c.parentPath), quote(c.path), quote(c.text));
        } else if(domChange instanceof Create) {
            final Create c = (Create)domChange;
            return joinString(CREATE, quote(c.path), quote(c.tag));
        } else {
            throw new IllegalStateException("Unsupported DomChange object type:" + domChange);
        }
    }

    @Override
    public void evalJs(int descriptor, String js) {
        final String message = addSquareBrackets(joinString(EVAL_JS, descriptor, quote(js)));
        messagesConsumer.accept(message);
    }

    private String joinString(Object... objects) {
        return String.join(",", Arrays.stream(objects).map(Object::toString).toArray(String[]::new));
    }

    private String quote(Object str) {
        return "\"" + str + "\"";
    }

    private String addSquareBrackets(String str) {
        return "["+ str + "]";
    }
}
