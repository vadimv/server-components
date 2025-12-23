package rsp.dsl;

import rsp.dom.DomEventEntry;
import rsp.dom.XmlNs;
import rsp.page.EventContext;
import rsp.util.ArrayUtils;

import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static rsp.server.http.HttpResponse.OK_STATUS_CODE;

/**
 * HTML tags definitions domain-specific language and related util functions.
 */
public final class Html {

    private Html() {}

    /**
     * Attributes names which are interpreted by default as properties.
     * @see #attr(String, String)
     */
    public static final String DEFAULT_PROPERTIES_NAMES =
            "autofocus, autoplay, async, checked, controls, defer, disabled, hidden, loop, multiple, open, readonly, required, scoped, selected, value";


    /**
     * Defines a type of web page:
     * * server-side single-page applications (SPAs), written in Java, e.g. for an admin UI
     * * plain server-rendered detached HTML pages
     * The page's head tag DSL determines if this page is an interactive Single-Page-Application or a plain HTML page.
     * The head(...) or head(HeadType.SPA, ...) function creates an HTML page head tag for an SPA.
     * If the head() is not present in the page's markup, the simple SPA-type header is added automatically.
     * This type of head injects a script, which establishes a WebSocket connection between the browser's page and the server
     * and enables reacting to the browser events.
     * head(HeadType.PLAIN, ...) renders the markup with the head tag without injecting of init script
     * to establish a connection with server and enable server side events handling for SPA.
     * This results in rendering of a plain detached HTML page.
     */
    public enum HeadType { SPA, PLAIN }

    /**
     * An HTML {@literal <html>} element, the root element of an HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static HtmlDocument html(final Definition... children) {
        return new HtmlDocument(OK_STATUS_CODE, Map.of(), children);
    }

    /**
     * An XML tag.
     * @param ns an XML namespace
     * @param name an element name
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag xmlTag(final XmlNs ns, final String name, final Definition... children) {
        return new Tag(ns, name, children);
    }

    /**
     * A self closing XML tag.
     * @param ns an XML namespace
     * @param name an element name
     * @param attributes attributes definitions of this element
     * @return a tag definition
     */
    public static SelfClosingTag selfClosingXmlTag(final XmlNs ns, final String name, final Attribute... attributes) {
        return new SelfClosingTag(ns, name, attributes);
    }

    /**
     * An arbitrary HTML element.
     * @param name an element name
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag tag(final String name, final Definition... children) {
        return xmlTag(XmlNs.html, name, children);
    }

    /**
     * A void element.
     * @param name an element name
     * @param attributes  attributes of this element
     * @return a tag definition
     */
    public static SelfClosingTag selfClosingTag(final String name, final Attribute... attributes) {
        return selfClosingXmlTag(XmlNs.html, name, attributes);
    }

    /**
     * A HTML element's attribute.
     * @param name an attribute name
     * @param value an attribute value
     * @param isProperty true if this attribute should be interpreted as a property, false otherwise
     * @return an attribute definition
     */
    public static Attribute attr(final String name, final String value, final boolean isProperty) {
        return new Attribute(name, value, isProperty);
    }

    /**
     * An element's property attribute.
     * @param name a property name
     * @param value a property value
     * @return a property definition
     */
    public static Attribute prop(final String name, final String value) {
        return attr(name, value, true);
    }

    /**
     * An HTML element's attribute.
     * This attribute is interpreted as a property if its name is one of the properties by default:
     * {@value #DEFAULT_PROPERTIES_NAMES}.
     * @param name an attribute name
     * @param value an attribute value
     * @return an attribute definition
     */
    public static Attribute attr(final String name, final String value) {
        return attr(name, value, isPropertyByDefault(name));
    }

    /**
     * A boolean attribute.
     * @param name an attribute name
     * @return an attribute definition
     */
    public static Attribute attr(final String name) {
        return new Attribute(name, name, isPropertyByDefault(name));
    }

    /**
     * A DOM event handler definition.
     * @param eventType an event name
     * @param handler an event handler
     * @return a DOM event handler definition
     */
    public static EventDefinition on(final String eventType, final Consumer<EventContext> handler) {
        return new EventDefinition(eventType, handler, DomEventEntry.NO_MODIFIER);
    }

    /**
     * A DOM event handler definition.
     * @param eventType an event name
     * @param preventDefault true if the event does not get explicitly handled,
     *                        its default action should not be taken as it normally would be, false otherwise
     * @param handler an event handler
     * @return a DOM event handler definition
     */
    public static EventDefinition on(final String eventType, final boolean preventDefault, final Consumer<EventContext> handler) {
        return new EventDefinition(eventType, handler, preventDefault, DomEventEntry.NO_MODIFIER);
    }

    /**
     * An element's inline style.
     * @param name a style name
     * @param value a style value
     * @return an inline style definition
     */
    public static StyleDefinition style(final String name, final String value) {
        return new StyleDefinition(name, value);
    }

    /**
     * An element's text content.
     * @param text a text as a {@link String}
     * @return a text node definition
     */
    public static Text text(final String text) {
        return new Text(text);
    }

    /**
     * An element's text content, for an input class other than q {@link String}.
     * @param obj an arbitrary object to be converted to text using its {@link #toString()} method
     * @return a text node definition
     */
    public static Text text(final Object obj) {
        return new Text(String.valueOf(obj));
    }


    /**
     * A HTML {@literal <body>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag body(final Definition... children) {
        return tag("body", children);
    }

    /**
     * An HTML {@literal <head>} element of an HTML document.
     * This element will contain a script which establishes a connection to the server and enables a single-page application.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag head(final Definition... children) {
        return head(HeadType.SPA, children);
    }

    /**
     * An HTML {@literal <head>} element of an HTML document,
     * has not to be upgraded with the script element establishing
     * a WebSocket connection to the server after the browser loads the page.
     * No live page session will be created on the server in this case.
     * @param headType a type of this {@literal <head>} element.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag head(final HeadType headType, final Definition... children) {
        Objects.requireNonNull(headType);
        return headType == HeadType.SPA ? tag("head", children) : new PlainTag(XmlNs.html, "head", children);
    }

    /**
     * A HTML {@literal <title>} element of a HTML document.
     * @param text a document's title text
     * @return a tag definition
     */
    public static Tag title(final String text) {
        return tag("title", text(text));
    }


    public static Tag script(final Definition... children) {
        return tag("script", children);
    }

    /**
     * A HTML {@literal <link>} element of a HTML document.
     * @param children the element's attributes
     * @return a tag definition
     */
    public static Tag link(final Attribute... children) {
        return tag("link", children);
    }

    /**
     * A HTML {@literal <meta>} element of a HTML document.
     * @param children the element's attributes
     * @return a tag definition
     */
    public static Tag meta(final Attribute... children) {
        return tag("meta", children);
    }

    /**
     * A HTML {@literal <h1>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return  a tag definition
     */
    public static Tag h1(final Definition... children) {
        return tag("h1", children);
    }

    /**
     * A HTML {@literal <h1>} element of a HTML document.
     * @param text the element's text content
     * @return  a tag definition
     */
    public static Tag h1(final String text) {
        return h1(text(text));
    }

    /**
     * A HTML {@literal <h2>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return  a tag definition
     */
    public static Tag h2(final Definition... children) {
        return tag("h2", children);
    }

    /**
     * A HTML {@literal <h2>} element of a HTML document.
     * @param text the element's text content
     * @return  a tag definition
     */
    public static Tag h2(final String text) {
        return h2(text(text));
    }

    /**
     * A HTML {@literal <h3>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag h3(final Definition... children) {
        return tag("h3", children);
    }

    /**
     * A HTML {@literal <h3>} element of a HTML document.
     * @param text the element's text content
     * @return  a tag definition
     */
    public static Tag h3(final String text) {
        return h3(text(text));
    }

    /**
     * A HTML {@literal <h4>} element of a HTML document
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag h4(final Definition... children) {
        return tag("h4", children);
    }

    /**
     * A HTML {@literal <h4>} element of a HTML document.
     * @param text the element's text content
     * @return  a tag definition
     */
    public static Tag h4(final String text) {
        return h4(text(text));
    }

    /**
     * A HTML {@literal <h5>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag h5(final Definition... children) {
        return tag("h5", children);
    }

    /**
     * A HTML {@literal <h5>} element of a HTML document.
     * @param text the element's text content
     * @return  a tag definition
     */
    public static Tag h5(final String text) {
        return h5(text(text));
    }

    /**
     * A HTML {@literal <h6>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag h6(final Definition... children) {
        return tag("h6", children);
    }

    /**
     * A HTML {@literal <h6>} element of a HTML document.
     * @param text the element's text content
     * @return  a tag definition
     */
    public static Tag h6(final String text) {
        return h6(text(text));
    }

    /**
     * A HTML {@literal <div>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag div(final Definition... children) {
        return tag("div", children);
    }

    /**
     * A HTML {@literal <div>} element of a HTML document.
     * @param text text content
     * @return a tag definition
     */
    public static Tag div(final String text) {
        return div(text(text));
    }

    /**
     * A HTML {@literal <a>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag a(final Definition... children) {
        return tag("a", children);
    }

    /**
     * An HTML {@literal <a>}, or anchor element of an HTML document.
     * @param href the URL that the hyperlink points to
     * @param text the link's destination text content
     * @param children other descendants definitions of this element
     * @return a tag definition
     */
    public static Tag a(final String href, final String text, final Definition... children) {
        return a(ArrayUtils.concat(new Definition[]{ attr("href", href), text(text)}, children));
    }

    /**
     * A HTML {@literal <p>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag p(final Definition... children) {
        return tag("p", children);
    }

    /**
     * A HTML {@literal <p>} element of a HTML document.
     * @param text text content
     * @return a tag definition
     */
    public static Tag p(final String text) {
        return p(text(text));
    }

    /**
     * A HTML {@literal <span>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag span(final Definition... children) {
        return tag("span", children);
    }

    /**
     * A HTML {@literal <span>} element of a HTML document.
     * @param text text content
     * @return a tag definition
     */
    public static Tag span(final String text) {
        return span(text(text));
    }

    /**
     * A HTML {@literal <form>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag form(final Definition... children) {
        return tag("form", children);
    }

    /**
     * A HTML {@literal <input>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag input(final Definition... children) {
        return tag("input", children);
    }

    /**
     * A HTML {@literal <button>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag button(final Definition... children) {
        return tag("button", children);
    }

    /**
     * A HTML {@literal <ul>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag ul(final Definition... children) {
        return tag("ul", children);
    }

    /**
     * A HTML {@literal <ol>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag ol(final Definition... children) {
        return tag("ol", children);
    }

    /**
     * A HTML {@literal <li>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag li(final Definition... children) {
        return tag("li", children);
    }

    /**
     * A HTML {@literal <li>} element of a HTML document.
     * @param text text content
     * @return a tag definition
     */
    public static Tag li(final String text) {
        return li(text(text));
    }

    /**
     * A HTML {@literal <table>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag table(final Definition... children) {
        return tag("table", children);
    }

    /**
     * A HTML {@literal <thead>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag thead(final Definition... children) {
        return tag("thead", children);
    }

    /**
     * A HTML {@literal <tbody>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag tbody(final Definition... children) {
        return tag("tbody", children);
    }

    /**
     * A HTML {@literal <th>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag th(final Definition... children) {
        return tag("th", children);
    }

    /**
     * A HTML {@literal <th>} element of a HTML document.
     * @param text text content
     * @return a tag definition
     */
    public static Tag th(final String text) {
        return th(text(text));
    }

    /**
     * A HTML {@literal <tr>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag tr(final Definition... children) {
        return tag("tr", children);
    }

    /**
     * A HTML {@literal <td>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag td(final Definition... children) {
        return tag("td", children);
    }

    /**
     * A HTML {@literal <td>} element of a HTML document.
     * @param text text content
     * @return a tag definition
     */
    public static Tag td(final String text) {
        return td(text(text));
    }

    /**
     * A HTML {@literal <label>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static Tag label(final Definition... children) {
        return tag("label", children);
    }

    /**
     * A HTML {@literal <br />} element of a HTML document.
     * @return a tag definition
     */
    public static SelfClosingTag br() {
        return selfClosingTag("br");
    }

    /**
     * Inserts a zero or more definitions provided as a stream.
     * @param items varargs of definitions
     * @return a document part definition representing a sequence of definitions
     */
    public static Definition of(Definition... items) {
        return new SequenceDefinition(items);
    }

    /**
     * Inserts a zero or more definitions provided as a stream.
     * @param items a {@link Stream} of definitions
     * @return a document part definition representing a sequence of definitions
     */
    public static Definition of(final Stream<Definition> items) {
        Objects.requireNonNull(items);
        return new SequenceDefinition(items.toArray(Definition[]::new));
    }

    /**
     * Inserts a definition which is a result of some code execution.
     * This functions allows mix declarative DOM tree definitions and code fragments.
     * @param itemSupplier a code block
     * @return a result definition
     */
    public static Definition of(final Supplier<Definition> itemSupplier) {
        Objects.requireNonNull(itemSupplier);
        return new SequenceDefinition(new Definition[] { itemSupplier.get() });
    }

    /**
     * Inserts a definition which is a result of a {@link CompletableFuture} completion.
     * @param completableFutureDefinition an asynchronous computation of a definition
     * @return a result definition
     */
    public static Definition of(final CompletableFuture<? extends Definition> completableFutureDefinition) {
        Objects.requireNonNull(completableFutureDefinition);
        return completableFutureDefinition.join();
    }

    /**
     * Inserts a document part definition provided as an argument if condition is true, otherwise inserts an empty definition.
     * @param condition a condition to check
     * @param then a definition which may be inserted
     * @return a result definition
     */
    public static Definition when(final boolean condition, final Definition then) {
        Objects.requireNonNull(then);
        return when(condition, () -> then);
    }

    /**
     * A lazy form of conditional function.
     * Inserts a document part definition provided as in a {@link Supplier} if condition is true, otherwise inserts an empty definition.
     * @param condition a condition to check
     * @param then a {@link Supplier} of a definition which may be inserted
     * @return a result definition
     */
    public static Definition when(final boolean condition, final Supplier<Definition> then) {
        Objects.requireNonNull(then);
        return condition ? then.get() : EmptyDefinition.INSTANCE;
    }

    /**
     * Provides a definition of a browsers' window object.
     * @return a window object definition
     */
    public static Window window() {
        return new Window();
    }

    /**
     * Creates reference to an HTML element which can be used as a key for obtaining its element's properties values.
     * @see EventContext#propertiesByRef(rsp.ref.ElementRef)
     * @return a reference object
     */
    public static rsp.ref.ElementRef createElementRef() {
        return new rsp.ref.ElementRef() {};
    }

    /**
     * An element ID DSL directive binds its parent element to the reference provided.
     * @param ref the reference which could be used to access the bind element's properties
     * @return a rendering hint definition, not added to the result HTML tree
     */
    public static ElementRef elementId(rsp.ref.ElementRef ref) {
        return new ElementRef(ref);
    }


    private static boolean isPropertyByDefault(final String name) {
        return DEFAULT_PROPERTIES_NAMES.contains(name);
    }
}
