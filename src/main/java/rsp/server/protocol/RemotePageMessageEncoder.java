package rsp.server.protocol;

import rsp.dom.DomEventEntry;
import rsp.dom.XmlNs;
import rsp.dom.TreePositionPath;
import rsp.dom.DefaultDomChangesContext.*;
import rsp.server.RemoteOut;

import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import static rsp.util.json.JsonUtils.escape;

/**
 * The implementation of the text-based protocol is based on the protocol of the Korolev project by Aleksey Fomkin.
 */
public final class RemotePageMessageEncoder implements RemoteOut {
    private static final int SET_RENDER_NUM = 0; // (n)
    private static final int CLEAN_ROOT = 1; // ()
    private static final int LISTEN_EVENT = 2; // (type, preventDefault, id, modifier)
    private static final int EXTRACT_PROPERTY = 3; // (descriptor, id, propertyName )
    private static final int MODIFY_DOM = 4; // (commands)
    private static final int FOCUS = 5; // (id) {
    private static final int CHANGE_PAGE_URL = 6; // (componentPath)
    private static final int UPLOAD_FORM = 7; // (id, descriptor)
    private static final int RELOAD_CSS = 8; // ()
    private static final int KEEP_ALIVE = 9; // ()
    private static final int EVAL_JS = 10; // (code)
    private static final int EXTRACT_EVENT_DATA = 11; // (descriptor, renderNum)
    private static final int LIST_FILES = 12; // (id, descriptor)
    private static final int UPLOAD_FILE = 13; // (id, descriptor, fileName)
    private static final int REST_FORM = 14; // (id)
    private static final int FORGET_EVENT = 15; // (type, id)

    // MODIFY_DOM commands
    private static final int  CREATE = 0; // (id, childId, xmlNs, tag)
    private static final int  CREATE_TEXT = 1; // (id, childId, text)
    private static final int  REMOVE = 2; // (id, childId)
    private static final int  SET_ATTR = 3; // (id, xmlNs, name, value, isProperty)
    private static final int  REMOVE_ATTR = 4; // (id, xmlNs, name, isProperty)
    private static final int  SET_STYLE = 5; // (id, name, value)
    private static final int  REMOVE_STYLE = 6; // (id, name)

    // EVENT modifier
    private static final int  NO_EVENT_MODIFIER = 0;
    private static final int  THROTTLE_EVENT_MODIFIER = 1;
    private static final int  DEBOUNCE_EVENT_MODIFIER = 2;

    //SET URL LOCATION type
    private static final int  HREF_LOCATION_TYPE = 0;
    private static final int  PATHNAME_LOCATION_TYPE = 1;
    private static final int  HASH_LOCATION_TYPE = 2;
    private static final int  SEARCH_LOCATION_TYPE = 3;
    private static final int  PUSH_STATE_TYPE = 4;

    private final Consumer<String> messagesOut;

    public RemotePageMessageEncoder(final Consumer<String> messagesOut) {
        this.messagesOut = messagesOut;
    }

    @Override
    public void setRenderNum(final int renderNum) {
        final String message = addSquareBrackets(joinString(SET_RENDER_NUM, renderNum));
        messagesOut.accept(message);
    }

    @Override
    public void listenEvents(final List<DomEventEntry> events) {
        if (events.size() > 0) {
            final String[] changes = events.stream().map(e -> joinString(quote(e.eventName),
                                                                         e.preventDefault,
                                                                         quote(e.eventTarget.elementPath().toString()),
                                                                         quote(modifierString(e.modifier)))).toArray(String[]::new);
            final String message = addSquareBrackets(joinString(LISTEN_EVENT,
                                                                joinString(changes)));
            messagesOut.accept(message);
        }
    }

    @Override
    public void forgetEvent(final String eventType, final TreePositionPath path) {
        final String message = addSquareBrackets(joinString(FORGET_EVENT,
                                                            quote(escape(eventType)),
                                                            quote(path.toString())));
        messagesOut.accept(message);
    }

    private static String modifierString(final DomEventEntry.Modifier eventModifier) {
        if (eventModifier instanceof DomEventEntry.ThrottleModifier(int timeFrameMs)) {
            return THROTTLE_EVENT_MODIFIER + ":" + timeFrameMs;
        } else if (eventModifier instanceof DomEventEntry.DebounceModifier(int waitMs, boolean immediate)) {
            return DEBOUNCE_EVENT_MODIFIER + ":" + waitMs + ":" + immediate;
        } else {
            return Integer.toString(NO_EVENT_MODIFIER);
        }
    }

    @Override
    public void extractProperty(final int descriptor, final TreePositionPath path, final String name) {
        final String message = addSquareBrackets(joinString(EXTRACT_PROPERTY,
                                                            quote(descriptor),
                                                            quote(path),
                                                            quote(escape(name))));
        messagesOut.accept(message);
    }

    @Override
    public void modifyDom(final List<DomChange> domChanges) {
        if (!domChanges.isEmpty()) {
            final String[] changes = domChanges.stream().map(this::modifyDomMessageBody).toArray(String[]::new);
            final String message = addSquareBrackets(joinString(MODIFY_DOM,
                                                                joinString(changes)));
            messagesOut.accept(message);
        }
    }

    @Override
    public void setHref(final String path) {
        final String message = addSquareBrackets(joinString(CHANGE_PAGE_URL, HREF_LOCATION_TYPE, quote(path)));
        messagesOut.accept(message);
    }

    @Override
    public void pushHistory(final String path) {
        final String message = addSquareBrackets(joinString(CHANGE_PAGE_URL, PUSH_STATE_TYPE, quote(path)));
        messagesOut.accept(message);
    }

    private String modifyDomMessageBody(final DomChange domChange) {
        return switch (domChange) {
            case RemoveAttr c -> joinString(REMOVE_ATTR, quote(c.path()), xmlNsString(c.xmlNs()), quote(escape(c.name())), c.isProperty());
            case RemoveStyle(TreePositionPath path, String name) -> joinString(REMOVE_STYLE, quote(path), quote(escape(name)), false);
            case Remove c -> joinString(REMOVE, quote(c.parentPath()), quote(c.path()));
            case SetAttr c -> joinString(SET_ATTR, quote(c.path()), xmlNsString(c.xmlNs()), quote(escape(c.name())), quote(c.value()), c.isProperty());
            case SetStyle c -> joinString(SET_STYLE, quote(c.path()), quote(escape(c.name())), quote(escape(c.value())));
            case CreateText c -> joinString(CREATE_TEXT, quote(c.parentPath()), quote(c.path()), quote(escape(c.text())));
            case Create c -> joinString(CREATE, quote(c.path().parent()), quote(c.path()), xmlNsString(c.xmlNs()), quote(escape(c.tag())));
        };
    }

    private String xmlNsString(final XmlNs xmlNs) {
        return xmlNs.uri().equals(XmlNs.html.uri()) ? "0" : quote(xmlNs.toString());
    }

    @Override
    public void evalJs(final int descriptor, final String js) {
        final String message = addSquareBrackets(joinString(EVAL_JS, descriptor, quote(js)));
        messagesOut.accept(message);
    }

    private String joinString(final String[] strings) {
        return String.join(",", strings);
    }

    private String joinString(final Object... objects) {
        return joinString(Arrays.stream(objects).map(Object::toString).toArray(String[]::new));
    }

    private String quote(final Object str) {
        return "\"" + str + "\"";
    }

    private String addSquareBrackets(final String str) {
        return "["+ str + "]";
    }
}
