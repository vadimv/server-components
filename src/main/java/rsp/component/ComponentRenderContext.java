package rsp.component;

import rsp.dom.*;
import rsp.page.*;
import rsp.page.events.Command;
import rsp.ref.Ref;

import java.util.*;
import java.util.function.*;

public class ComponentRenderContext implements RenderContextFactory {

    private static final TreePositionPath ROOT_COMPONENT_PATH = TreePositionPath.of("1");

    protected final QualifiedSessionId sessionId;
    protected final Consumer<Command> remotePageMessagesOut;
    private final Deque<TagNode> tagsStack = new ArrayDeque<>();
    private final List<TreePositionPath> rootNodesPaths = new ArrayList<>();
    private final Deque<ComponentSegment<?>> componentsStack = new ArrayDeque<>();
    private String docType;

    protected ComponentContext componentContext;

    private TreePositionPath domPath;

    private ComponentSegment<?> rootComponent;

    public ComponentRenderContext(final QualifiedSessionId sessionId,
                                  final TreePositionPath startDomPath,
                                  final ComponentContext componentContext,
                                  final Consumer<Command> remotePageMessagesOut) {
        this.domPath = Objects.requireNonNull(startDomPath);
        this.sessionId = Objects.requireNonNull(sessionId);
        this.componentContext = componentContext;
        this.remotePageMessagesOut = Objects.requireNonNull(remotePageMessagesOut);
    }

    public void setComponentContext(final ComponentContext componentContext) {
        this.componentContext = componentContext;
    }

    public void setDocType(final String docType) {
        this.docType = docType;
    }

    public String docType() {
        return docType;
    }

    public <S> ComponentSegment<S> openComponent(final ComponentFactory<S> componentFactory) {
        final ComponentSegment<?> parent = componentsStack.peek();
        final TreePositionPath componentPath = parent == null ?
                ROOT_COMPONENT_PATH : parent.path().addChild(parent.directChildren().size() + 1);
        final ComponentSegment<S> newComponent = componentFactory.createComponent(sessionId,
                                                                           componentPath,
                                                                           this,
                                                                           componentContext,
                                                                           remotePageMessagesOut);
        openComponent(newComponent);
        return newComponent;
    }

    public <S> void openComponent(final ComponentSegment<S> component) {
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
        final ComponentSegment<?> component = componentsStack.peek();
        assert component != null;

        final TagNode parent = tagsStack.peek();
        final TagNode tag = new TagNode(xmlns, name, isSelfClosing);
        if (parent == null) {
            if (!component.isRootNodesEmpty()) {
                final TreePositionPath prevTag = rootNodesPaths.get(rootNodesPaths.size() - 1);
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
                final ComponentSegment<?> ascedantComponent = ascendantComponentsIterator.next();
                if (!ascedantComponent.hasStartNodeDomPath()) {
                    ascedantComponent.setStartNodeDomPath(domPath);
                } else {
                    break;
                }
            }
        }

    }

    public void closeNode(final String name, final boolean upgrade) {
        tagsStack.pop();
        domPath = domPath.parent();
    }

    public void addTextNode(final String text) {
        final ComponentSegment<?> component = componentsStack.peek();
        assert component != null;

        final TagNode parentTag = tagsStack.peek();
        if (parentTag == null) {
            if (!component.isRootNodesEmpty()) {
                if (component.getLastRootNode() instanceof TextNode prevTextNode) {
                    prevTextNode.addPart(text);
                } else {
                    final TreePositionPath prevTag = rootNodesPaths.get(rootNodesPaths.size() - 1);
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
            if (!parentTag.children.isEmpty() && parentTag.children.get(parentTag.children.size() - 1) instanceof TextNode prevTextNode) {
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
        tagsStack.peek().addAttribute(name, value, isProperty);
    }

    public void setStyle(final String name, final String value) {
        tagsStack.peek().addStyle(name, value);
    }

    public void addEvent(final TreePositionPath elementPath,
                         final String eventType,
                         final Consumer<EventContext> eventHandler,
                         final boolean preventDefault,
                         final DomEventEntry.Modifier modifier) {
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
        final ComponentSegment<?> component = componentsStack.peek();
        assert component != null;
        final TagNode tag = tagsStack.peek();
        assert tag != null;
        component.addRef(ref, domPath);
    }

    @Override
    public ComponentRenderContext newContext(final TreePositionPath startDomPath) {
        return new ComponentRenderContext(sessionId,
                                          startDomPath,
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


