package rsp.html;

import rsp.dom.Event;
import rsp.dom.XmlNs;
import rsp.page.EventContext;
import rsp.ref.ElementRef;
import rsp.util.ArrayUtils;

import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static rsp.server.http.HttpStatusCodes.OK_STATUS_CODE;

/**
 * HTML tags definitions domain-specific language and related util functions.
 */
public final class HtmlDsl {

    /**
     * Attributes names which are interpreted by default as properties.
     * @see #attr(String, String)
     */
    public final static String DEFAULT_PROPERTIES_NAMES =
            "autofocus, autoplay, async, checked, controls, defer, disabled, hidden, loop, multiple, open, readonly, required, scoped, selected, value";

    public enum HeadType { SPA, PLAIN }

    /**
     * A HTML {@literal <html>} element, the root element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static HtmlDocumentDefinition html(final SegmentDefinition... children) {
        return new HtmlDocumentDefinition(OK_STATUS_CODE, Map.of(), children);
    }

    /**
     * An XML tag.
     * @param ns an XML namespace
     * @param name an element name
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition xmlTag(final XmlNs ns, final String name, final SegmentDefinition... children) {
        return new TagDefinition(ns, name, children);
    }

    /**
     * An arbitrary HTML element.
     * @param name an element name
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition tag(final String name, final SegmentDefinition... children) {
        return xmlTag(XmlNs.html, name, children);
    }

    /**
     * A HTML element's attribute.
     * @param name an attribute name
     * @param value an attribute value
     * @param isProperty true if this attribute should be interpreted as a property, false otherwise
     * @return an attribute definition
     */
    public static AttributeDefinition attr(final String name, final String value, final boolean isProperty) {
        return new AttributeDefinition(name, value, isProperty);
    }

    /**
     * An element's property attribute.
     * @param name a property name
     * @param value a property value
     * @return a property definition
     */
    public static AttributeDefinition prop(final String name, final String value) {
        return attr(name, value, true);
    }

    /**
     * A HTML element's attribute.
     * This attribute is interpreted as a property if its name is one of the properties by default:
     * {@value #DEFAULT_PROPERTIES_NAMES}.
     * @param name an attribute name
     * @param value an attribute value
     * @return an attribute definition
     */
    public static AttributeDefinition attr(final String name, final String value) {
        return attr(name, value, isPropertyByDefault(name));
    }

    /**
     * A boolean attribute.
     * @param name an attribute name
     * @return an attribute definition
     */
    public static AttributeDefinition attr(final String name) {
        return new AttributeDefinition(name, name, isPropertyByDefault(name));
    }

    /**
     * A DOM event handler definition.
     * @param eventType an event name
     * @param handler an event handler
     * @return a DOM event handler definition
     */
    public static EventDefinition on(final String eventType, final Consumer<EventContext> handler) {
        return new EventDefinition(eventType, handler, Event.NO_MODIFIER);
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
        return new EventDefinition(eventType, handler, preventDefault, Event.NO_MODIFIER);
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
    public static TextDefinition text(final String text) {
        return new TextDefinition(text);
    }

    /**
     * An element's text content, for a input class other than q {@link String}.
     * @param obj an arbitrary object to be converted to text using its {@link #toString()} method
     * @return a text node definition
     */
    public static TextDefinition text(final Object obj) {
        return new TextDefinition(obj.toString());
    }


    /**
     * A HTML {@literal <body>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition body(final SegmentDefinition... children) {
        return tag("body", children);
    }

    /**
     * A HTML {@literal <head>} element of a HTML document.
     * This element will contain a script which establishes a connection to the server and enables a single-page application.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition head(final SegmentDefinition... children) {
        return head(HeadType.SPA, children);
    }

    /**
     * A HTML {@literal <head>} element of a HTML document,
     * has not to be upgraded with the script element establishing
     * a WebSocket connection to the server after the browser loads the page.
     * No live page session will be created on the server in this case.
     * @param headType a type of this {@literal <head>} element.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition head(final HeadType headType, final SegmentDefinition... children) {
        return headType == HeadType.SPA ? tag("head", children) : new PlainTagDefinition(XmlNs.html, "head", children);
    }

    /**
     * A HTML {@literal <title>} element of a HTML document.
     * @param text a document's title text
     * @return a tag definition
     */
    public static TagDefinition title(final String text) {
        return tag("title", text(text));
    }

    /**
     * A HTML {@literal <link>} element of a HTML document.
     * @param children the element's attributes
     * @return a tag definition
     */
    public static TagDefinition link(final AttributeDefinition... children) {
        return tag("link", children);
    }

    /**
     * A HTML {@literal <meta>} element of a HTML document.
     * @param children the element's attributes
     * @return a tag definition
     */
    public static TagDefinition meta(final AttributeDefinition... children) {
        return tag("meta", children);
    }

    /**
     * A HTML {@literal <h1>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return  a tag definition
     */
    public static TagDefinition h1(final SegmentDefinition... children) {
        return tag("h1", children);
    }

    /**
     * A HTML {@literal <h1>} element of a HTML document.
     * @param text the element's text content
     * @return  a tag definition
     */
    public static TagDefinition h1(final String text) {
        return h1(text(text));
    }

    /**
     * A HTML {@literal <h2>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return  a tag definition
     */
    public static TagDefinition h2(final SegmentDefinition... children) {
        return tag("h2", children);
    }

    /**
     * A HTML {@literal <h2>} element of a HTML document.
     * @param text the element's text content
     * @return  a tag definition
     */
    public static TagDefinition h2(final String text) {
        return h2(text(text));
    }

    /**
     * A HTML {@literal <h3>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition h3(final SegmentDefinition... children) {
        return tag("h3", children);
    }

    /**
     * A HTML {@literal <h3>} element of a HTML document.
     * @param text the element's text content
     * @return  a tag definition
     */
    public static TagDefinition h3(final String text) {
        return h3(text(text));
    }

    /**
     * A HTML {@literal <h4>} element of a HTML document
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition h4(final SegmentDefinition... children) {
        return tag("h4", children);
    }

    /**
     * A HTML {@literal <h4>} element of a HTML document.
     * @param text the element's text content
     * @return  a tag definition
     */
    public static TagDefinition h4(final String text) {
        return h4(text(text));
    }

    /**
     * A HTML {@literal <h5>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition h5(final SegmentDefinition... children) {
        return tag("h5", children);
    }

    /**
     * A HTML {@literal <h5>} element of a HTML document.
     * @param text the element's text content
     * @return  a tag definition
     */
    public static TagDefinition h5(final String text) {
        return h5(text(text));
    }

    /**
     * A HTML {@literal <h6>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition h6(final SegmentDefinition... children) {
        return tag("h6", children);
    }

    /**
     * A HTML {@literal <h6>} element of a HTML document.
     * @param text the element's text content
     * @return  a tag definition
     */
    public static TagDefinition h6(final String text) {
        return h6(text(text));
    }

    /**
     * A HTML {@literal <div>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition div(final SegmentDefinition... children) {
        return tag("div", children);
    }

    /**
     * A HTML {@literal <div>} element of a HTML document.
     * @param text text content
     * @return a tag definition
     */
    public static TagDefinition div(final String text) {
        return div(text(text));
    }

    /**
     * A HTML {@literal <a>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition a(final SegmentDefinition... children) {
        return tag("a", children);
    }

    /**
     * A HTML {@literal <a>}, or anchor element of a HTML document.
     * @param href the URL that the hyperlink points to
     * @param text the link's destination text content
     * @param children other descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition a(final String href, final String text, final SegmentDefinition... children) {
        return a(ArrayUtils.concat(new SegmentDefinition[]{ attr("href", href), text(text)}, children));
    }

    /**
     * A HTML {@literal <p>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition p(final SegmentDefinition... children) {
        return tag("p", children);
    }

    /**
     * A HTML {@literal <p>} element of a HTML document.
     * @param text text content
     * @return a tag definition
     */
    public static TagDefinition p(final String text) {
        return p(text(text));
    }

    /**
     * A HTML {@literal <span>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition span(final SegmentDefinition... children) {
        return tag("span", children);
    }

    /**
     * A HTML {@literal <span>} element of a HTML document.
     * @param text text content
     * @return a tag definition
     */
    public static TagDefinition span(final String text) {
        return span(text(text));
    }

    /**
     * A HTML {@literal <form>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition form(final SegmentDefinition... children) {
        return tag("form", children);
    }

    /**
     * A HTML {@literal <input>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition input(final SegmentDefinition... children) {
        return tag("input", children);
    }

    /**
     * A HTML {@literal <button>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition button(final SegmentDefinition... children) {
        return tag("button", children);
    }

    /**
     * A HTML {@literal <ul>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition ul(final SegmentDefinition... children) {
        return tag("ul", children);
    }

    /**
     * A HTML {@literal <ol>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition ol(final SegmentDefinition... children) {
        return tag("ol", children);
    }

    /**
     * A HTML {@literal <li>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition li(final SegmentDefinition... children) {
        return tag("li", children);
    }

    /**
     * A HTML {@literal <li>} element of a HTML document.
     * @param text text content
     * @return a tag definition
     */
    public static TagDefinition li(final String text) {
        return li(text(text));
    }

    /**
     * A HTML {@literal <table>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition table(final SegmentDefinition... children) {
        return tag("table", children);
    }

    /**
     * A HTML {@literal <thead>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition thead(final SegmentDefinition... children) {
        return tag("thead", children);
    }

    /**
     * A HTML {@literal <tbody>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition tbody(final SegmentDefinition... children) {
        return tag("tbody", children);
    }

    /**
     * A HTML {@literal <th>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition th(final SegmentDefinition... children) {
        return tag("th", children);
    }

    /**
     * A HTML {@literal <th>} element of a HTML document.
     * @param text text content
     * @return a tag definition
     */
    public static TagDefinition th(final String text) {
        return th(text(text));
    }

    /**
     * A HTML {@literal <tr>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition tr(final SegmentDefinition... children) {
        return tag("tr", children);
    }

    /**
     * A HTML {@literal <td>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition td(final SegmentDefinition... children) {
        return tag("td", children);
    }

    /**
     * A HTML {@literal <td>} element of a HTML document.
     * @param text text content
     * @return a tag definition
     */
    public static TagDefinition td(final String text) {
        return td(text(text));
    }

    /**
     * A HTML {@literal <label>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static TagDefinition label(final SegmentDefinition... children) {
        return tag("label", children);
    }

    /**
     * A HTML {@literal <br/>} element of a HTML document.
     * @return a tag definition
     */
    public static TagDefinition br() {
        return tag("br");
    }


    /**
     * Inserts a zero or more definitions provided as a stream.
     * @param items a {@link Stream} of definitions
     * @return a document part definition representing a sequence of definitions
     */
    public static SequenceDefinition of(final Stream<SegmentDefinition> items) {
        return new SequenceDefinition(items.toArray(SegmentDefinition[]::new));
    }

    /**
     * Inserts a definition which is a result of some code execution.
     * This functions allows to mix declarative DOM tree definitions and code fragments.
     * @param itemSupplier a code block
     * @return a result definition
     */
    public static SequenceDefinition of(final Supplier<SegmentDefinition> itemSupplier) {
        return new SequenceDefinition(new SegmentDefinition[] { itemSupplier.get() });
    }

    /**
     * Inserts a definition which is a result of a {@link CompletableFuture} completion.
     * @param completableFutureDefinition an asynchronous computation of a definition
     * @return a result definition
     */
    public static SegmentDefinition of(final CompletableFuture<? extends SegmentDefinition> completableFutureDefinition) {
        return completableFutureDefinition.join();
    }

    /**
     * Inserts a document part definition provided as an argument if condition is true, otherwise inserts an empty definition.
     * @param condition a condition to check
     * @param then a definition which may be inserted
     * @return a result definition
     */
    public static SegmentDefinition when(final boolean condition, final SegmentDefinition then) {
        return when(condition, () -> then);
    }

    /**
     * A lazy form of conditional function.
     * Inserts a document part definition provided as in a {@link Supplier} if condition is true, otherwise inserts an empty definition.
     * @param condition a condition to check
     * @param then a {@link Supplier} of a definition which may be inserted
     * @return a result definition
     */
    public static SegmentDefinition when(final boolean condition, final Supplier<SegmentDefinition> then) {
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
     * Creates reference to a HTML element which can be used as a key for obtaining its element's properties values.
     * @see EventContext#propertiesByRef(ElementRef)
     * @return a reference object
     */
    public static ElementRef createElementRef() {
        return new ElementRef() {};
    }

    public static ElementRefDefinition elementId(ElementRef ref) {
        return new ElementRefDefinition(ref);
    }

    private static boolean isPropertyByDefault(final String name) {
        return DEFAULT_PROPERTIES_NAMES.contains(name);
    }


}
