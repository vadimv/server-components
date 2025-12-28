package rsp.component;

import rsp.dom.*;
import rsp.page.*;
import rsp.page.events.Command;
import rsp.ref.Ref;

import java.util.*;
import java.util.function.*;

/**
 * A mutable collector of a component segments subtree.
 * The relevant methods of this class are invoked during rendering of a component.
 * @see ComponentSegment
 */
public class TreeBuilder implements TreeBuilderFactory {

    private static final TreePositionPath ROOT_COMPONENT_PATH = TreePositionPath.of("1");

    protected final QualifiedSessionId sessionId;
    protected final Consumer<Command> remotePageMessagesOut;

    private final Deque<TagNode> tagsStack = new ArrayDeque<>();
    private final List<TreePositionPath> rootNodesPaths = new ArrayList<>();
    private final Deque<ComponentSegment<?>> componentsStack = new ArrayDeque<>();

    protected ComponentContext componentContext;

    private String docType;
    private TreePositionPath domPath;
    private ComponentSegment<?> rootComponent;

    public TreeBuilder(final QualifiedSessionId sessionId,
                       final TreePositionPath startDomPath,
                       final ComponentContext componentContext,
                       final Consumer<Command> remotePageMessagesOut) {
        this.domPath = Objects.requireNonNull(startDomPath);
        this.sessionId = Objects.requireNonNull(sessionId);
        this.componentContext = componentContext;
        this.remotePageMessagesOut = Objects.requireNonNull(remotePageMessagesOut);
    }

    public void setComponentContext(final ComponentContext componentContext) {
        this.componentContext = Objects.requireNonNull(componentContext);
    }

    public void setDocType(final String docType) {
        this.docType = Objects.requireNonNull(docType);
    }

    public String docType() {
        return docType;
    }

    public <S> ComponentSegment<S> openComponent(final ComponentSegmentFactory<S> componentSegmentFactory) {
        Objects.requireNonNull(componentSegmentFactory);
        final ComponentSegment<?> parent = componentsStack.peek();
        final TreePositionPath componentPath = parent == null ?
                ROOT_COMPONENT_PATH : parent.path().addChild(parent.directChildren().size() + 1);
        final ComponentSegment<S> newComponent = componentSegmentFactory.createComponentSegment(sessionId,
                                                                                                componentPath,
                                                                                                this,
                                                                                                componentContext,
                                                                                                remotePageMessagesOut);
        openComponent(newComponent);
        return newComponent;
    }

    public <S> void openComponent(final ComponentSegment<S> component) {
        Objects.requireNonNull(component);
        if (rootComponent == null) {
            rootComponent = component;
        } else {
            final ComponentSegment<?> parentComponent = componentsStack.peek();
            assert parentComponent != null;
            parentComponent.addChild(component);
        }
        componentsStack.push(component);
    }

    public void closeComponent() {
        componentsStack.pop();
    }

    public void openNode(XmlNs xmlns, String name, boolean isSelfClosing) {
        Objects.requireNonNull(xmlns);
        Objects.requireNonNull(name);
        final ComponentSegment<?> component = componentsStack.peek();
        assert component != null;

        final TagNode parent = tagsStack.peek();
        final TagNode tag = new TagNode(xmlns, name, isSelfClosing);
        if (parent == null) {
            if (!component.isRootNodesEmpty()) {
                final TreePositionPath prevTag = rootNodesPaths.getLast();
                domPath = prevTag.incSibling();
            }
            rootNodesPaths.add(domPath);
        } else {
            final int nextChild = parent.children.size() + 1;
            domPath = domPath.addChild(nextChild);
            parent.addChild(tag);
        }
        tagsStack.push(tag);

        component.setStartNodeDomPath(domPath);
        component.addRootDomNode(domPath, tag);

        if (componentsStack.size() > 1) {
            var ascendantComponentsIterator = componentsStack.iterator();
            ascendantComponentsIterator.next();// skip a current component
            while (ascendantComponentsIterator.hasNext()) {
                final ComponentSegment<?> ascendantComponent = ascendantComponentsIterator.next();
                if (!ascendantComponent.hasStartNodeDomPath()) {
                    ascendantComponent.setStartNodeDomPath(domPath);
                } else {
                    break;
                }
            }
        }
    }

    public void closeNode(final String name, final boolean upgrade) {
        Objects.requireNonNull(name);
        tagsStack.pop();
        domPath = domPath.parent();
    }

    public void addTextNode(final String text) {
        Objects.requireNonNull(text);
        final ComponentSegment<?> component = componentsStack.peek();
        assert component != null;

        final TagNode parentTag = tagsStack.peek();
        if (parentTag == null) {
            if (!component.isRootNodesEmpty()) {
                if (component.getLastRootNode() instanceof TextNode prevTextNode) {
                    prevTextNode.addPart(text);
                } else {
                    final TreePositionPath prevTag = rootNodesPaths.getLast();
                    domPath = prevTag.incSibling();
                    rootNodesPaths.add(domPath);
                    component.setStartNodeDomPath(domPath);
                    component.addRootDomNode(domPath, new TextNode(text));
                }
            } else {
                rootNodesPaths.add(domPath);
                component.setStartNodeDomPath(domPath);
                component.addRootDomNode(domPath, new TextNode(text));
            }
        } else {
            if (!parentTag.children.isEmpty() && parentTag.children.getLast() instanceof TextNode prevTextNode) {
                prevTextNode.addPart(text);
            } else {
                final int nextChild = parentTag.children.size() + 1;
                domPath = domPath.addChild(nextChild);
                final TextNode newText = new TextNode(text);
                parentTag.addChild(newText);
                component.setStartNodeDomPath(domPath);
                component.addRootDomNode(domPath, newText);
                domPath  = domPath.parent();
            }
        }
    }

    public void setAttr(final XmlNs xmlNs, final String name, final String value, final boolean isProperty) {
        Objects.requireNonNull(xmlNs);
        Objects.requireNonNull(name);
        Objects.requireNonNull(value);
        tagsStack.peek().addAttribute(name, value, isProperty);
    }

    public void addEvent(final TreePositionPath elementPath,
                         final String eventType,
                         final Consumer<EventContext> eventHandler,
                         final boolean preventDefault,
                         final DomEventEntry.Modifier modifier) {
        Objects.requireNonNull(elementPath);
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(eventHandler);
        Objects.requireNonNull(modifier);
        final ComponentSegment<?> component = componentsStack.peek();
        assert component != null;
        component.addDomEventHandler(elementPath, eventType, eventHandler, preventDefault, modifier);
    }

    public void addEvent(final String eventType,
                         final Consumer<EventContext> eventHandler,
                         final boolean preventDefault,
                         final DomEventEntry.Modifier modifier) {
        final TagNode tag = tagsStack.peek();
        assert tag != null;
        addEvent(domPath, eventType, eventHandler, preventDefault, modifier);
    }

    public void addRef(final Ref ref) {
        Objects.requireNonNull(ref);
        final ComponentSegment<?> component = componentsStack.peek();
        assert component != null;
        final TagNode tag = tagsStack.peek();
        assert tag != null;
        component.addRef(ref, domPath);
    }

    @Override
    public TreeBuilder createTreeBuilder(final TreePositionPath baseDomPath) {
        return new TreeBuilder(sessionId,
                               baseDomPath,
                               componentContext,
                               remotePageMessagesOut);
    }

    public String html() {
        final StringBuilder sb = new StringBuilder();
        final HtmlBuilder hb = new HtmlBuilder(sb);
        if (docType != null) {
            sb.append(docType);
        }
        if (rootComponent != null) {
            rootComponent.html(hb);
        }
        return hb.toString();
    }

    public List<DomEventEntry> recursiveEvents() {
        if (rootComponent != null) {
            return rootComponent.recursiveDomEvents();
        } else {
            return List.of();
        }
    }

    public List<ComponentEventEntry> recursiveComponentEvents() {
        if (rootComponent != null) {
            return rootComponent.recursiveComponentEvents();
        } else {
            return List.of();
        }
    }

    public void shutdown() {
        if (rootComponent != null) {
            rootComponent.unmount();
        }
    }

    public Map<Ref, TreePositionPath> recursiveRefs() {
        if (rootComponent != null) {
            return rootComponent.recursiveRefs();
        } else {
            return Map.of();
        }
    }
}


