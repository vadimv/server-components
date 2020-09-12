package rsp.dom;

import rsp.Event;
import rsp.EventContext;
import rsp.RenderContext;
import rsp.XmlNs;

import java.util.*;
import java.util.function.Consumer;

public class DomTreeRenderContext<S> implements RenderContext<S> {

    public final Map<Path, Event> events = new HashMap();

    public Tag root;
    private Deque<Tag> tagsStack = new ArrayDeque<>();

    public DomTreeRenderContext() {
    }

    @Override
    public void openNode(XmlNs xmlns, String name) {
        if(root == null) {
            root = new Tag(new Path(1), xmlns, name);
            tagsStack.push(root);
        } else {
            final Tag parent = tagsStack.peek();
            final int nextChild = parent.children.size() + 1;
            final Tag newTag = new Tag(parent.path.childNumber(nextChild), xmlns, name);
            parent.addChild(newTag);
            tagsStack.push(newTag);
        }
    }

    @Override
    public void closeNode(String name) {
        tagsStack.pop();
    }

    @Override
    public void setAttr(XmlNs xmlNs, String name, String value) {
        tagsStack.peek().addAttribute(name, value);
    }

    @Override
    public void setStyle(String name, String value) {
        tagsStack.peek().addStyle(name, value);
    }

    @Override
    public void addTextNode(String text) {
        tagsStack.peek().addChild(new Text(tagsStack.peek().path, text));
    }

    @Override
    public void addEvent(String eventType, Consumer<EventContext> eventHandler) {
        final Path eventPath = tagsStack.peek().path;
        events.put(eventPath, new Event(eventType, eventPath, eventHandler));
    }

}


