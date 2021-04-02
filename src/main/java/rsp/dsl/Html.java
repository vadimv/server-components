package rsp.dsl;

import rsp.Render;
import rsp.dom.Event;
import rsp.dom.XmlNs;
import rsp.page.EventContext;
import rsp.page.PageRenderContext;
import rsp.ref.ElementRef;
import rsp.ref.TimerRef;
import rsp.util.ArrayUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A HTML tags definitions domain-specific language and related util functions.
 */
public final class Html<S> extends TagDefinition<S> {

    /**
     * Attributes names which are interpreted by default as properties.
     * @see #attr(String, String)
     */
    public final static String DEFAULT_PROPERTIES_NAMES =
            "autofocus, autoplay, async, checked, controls, defer, disabled, hidden, loop, multiple, open, readonly, required, scoped, selected, value";

    private static final int OK_STATUS_CODE = 200;
    private static final int MOVED_TEMPORARILY_STATUS_CODE = 302;

    private final int statusCode;
    private final Map<String, String> headers;

    @SafeVarargs
    private Html(int statusCode, Map<String, String> headers, DocumentPartDefinition<S>... children) {
        super(XmlNs.html, "html", children);
        this.statusCode = statusCode;
        this.headers = Objects.requireNonNull(headers);
    }

    @Override
    public void accept(PageRenderContext renderContext) {
        renderContext.setStatusCode(statusCode);
        renderContext.setHeaders(headers);
        renderContext.setDocType("<!DOCTYPE html>");
        super.accept(renderContext);
    }

    /**
     * Sets the HTTP status code to be rendered in the response.
     * @param statusCode status code
     * @return an instance with the status code
     */
    public Html<S> statusCode(int statusCode) {
        return new Html<>(statusCode, this.headers, this.children);
    }

    /**
     * Adds the HTTP headers to be rendered in the response.
     * @param headers the map containing headers
     * @return an instance with added headers
     */
    public Html<S> addHeaders(Map<String, String> headers) {
        return new Html<>(this.statusCode, merge(this.headers, headers), this.children);
    }

    /**
     * Sets redirect status code and the Location header.
     * @param location Location header for redirection
     * @return and instance with the redirection status code and header
     */
    public Html<S> redirect(String location) {
        return new Html<>(MOVED_TEMPORARILY_STATUS_CODE, merge(this.headers, Map.of("Location", location)), this.children);
    }

    /**
     * A HTML {@literal <html>} element, the root element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> Html<S> html(DocumentPartDefinition<S>... children) {
        return new Html<>(OK_STATUS_CODE, Map.of(), children);
    }

    /**
     * An XML tag.
     * @param ns an XML namespace
     * @param name an element name
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> xmlTag(XmlNs ns, String name, DocumentPartDefinition<S>... children) {
        return new TagDefinition<>(ns, name, children);
    }

    /**
     * An arbitrary HTML element.
     * @param name an element name
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    public static <S> TagDefinition<S> tag(String name, DocumentPartDefinition<S>... children) {
        return xmlTag(XmlNs.html, name, children);
    }

    /**
     * A HTML element's attribute.
     * @param name an attribute name
     * @param value an attribute value
     * @param isProperty true if this attribute should be interpreted as a property, false otherwise
     * @return an attribute definition
     */
    public static <S> AttributeDefinition<S> attr(String name, String value, boolean isProperty) {
        return new AttributeDefinition<>(name, value, isProperty);
    }

    /**
     * An element's property attribute.
     * @param name a property name
     * @param value a property value
     * @return a property definition
     */
    public static <S> AttributeDefinition<S> prop(String name, String value) {
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
    public static <S> AttributeDefinition<S> attr(String name, String value) {
        return attr(name, value, isPropertyByDefault(name));
    }

    /**
     * A boolean attribute.
     * @param name an attribute name
     * @return an attribute definition
     */
    public static <S> AttributeDefinition<S> attr(String name) {
        return new AttributeDefinition<>(name, name, isPropertyByDefault(name));
    }

    /**
     * A DOM event handler definition.
     * @param eventType an event name
     * @param handler an event handler
     * @return a DOM event handler definition
     */
    public static <S> EventDefinition<S> on(String eventType, Consumer<EventContext<S>> handler) {
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
    public static <S> EventDefinition<S> on(String eventType, boolean preventDefault, Consumer<EventContext<S>> handler) {
        return new EventDefinition<S>(eventType, handler, preventDefault, Event.NO_MODIFIER);
    }

    /**
     * An element's inline style.
     * @param name a style name
     * @param value a style value
     * @return an inline style definition
     */
    public static <S> StyleDefinition<S> style(String name, String value) {
        return new StyleDefinition<>(name, value);
    }

    /**
     * An element's text content.
     * @param text a text as a {@link String}
     * @return a text node definition
     */
    public static <S> TextDefinition<S> text(String text) {
        return new TextDefinition<>(text);
    }

    /**
     * An element's text content, for a input class other than q {@link String}.
     * @param obj an arbitrary object to be converted to text using its {@link #toString()} method
     * @return a text node definition
     */
    public static <S>  TextDefinition<S> text(Object obj) {
        return new TextDefinition<>(obj.toString());
    }


    /**
     * A HTML {@literal <body>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> body(DocumentPartDefinition<S>... children) {
        return tag("body", children);
    }

    /**
     * A HTML {@literal <head>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> head(DocumentPartDefinition<S>... children) {
        return tag("head", children);
    }

    /**
     * A 'plain' HTML {@literal <head>} element of a HTML document,
     * has not to be upgraded with the script element establishing
     * a WebSocket connection to the server after the browser loads the page.
     * No live page session will be created on the server in this case.
     * @param children descendants definitions of this element
     * @param <S>
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> headP(DocumentPartDefinition<S>... children) {
        return new PlainTagDefinition<>(XmlNs.html, "head", children);
    }

    /**
     * A HTML {@literal <title>} element of a HTML document.
     * @param text a document's title text
     * @return a tag definition
     */
    public static <S> TagDefinition<S> title(String text) {
        return tag("title", text(text));
    }

    /**
     * A HTML {@literal <link>} element of a HTML document.
     * @param children the element's attributes
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> link(AttributeDefinition<S>... children) {
        return tag("link", children);
    }

    /**
     * A HTML {@literal <meta>} element of a HTML document.
     * @param children the element's attributes
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> meta(AttributeDefinition<S>... children) {
        return tag("meta", children);
    }

    /**
     * A HTML {@literal <h1>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return  a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> h1(DocumentPartDefinition<S>... children) {
        return tag("h1", children);
    }

    /**
     * A HTML {@literal <h1>} element of a HTML document.
     * @param text the element's text content
     * @return  a tag definition
     */
    public static <S> TagDefinition<S> h1(String text) {
        return h1(text(text));
    }

    /**
     * A HTML {@literal <h2>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return  a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> h2(DocumentPartDefinition<S>... children) {
        return tag("h2", children);
    }

    /**
     * A HTML {@literal <h2>} element of a HTML document.
     * @param text the element's text content
     * @return  a tag definition
     */
    public static <S> TagDefinition<S> h2(String text) {
        return h2(text(text));
    }

    /**
     * A HTML {@literal <h3>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> h3(DocumentPartDefinition<S>... children) {
        return tag("h3", children);
    }

    /**
     * A HTML {@literal <h3>} element of a HTML document.
     * @param text the element's text content
     * @return  a tag definition
     */
    public static <S> TagDefinition<S> h3(String text) {
        return h3(text(text));
    }

    /**
     * A HTML {@literal <h4>} element of a HTML document
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> h4(DocumentPartDefinition<S>... children) {
        return tag("h4", children);
    }

    /**
     * A HTML {@literal <h4>} element of a HTML document.
     * @param text the element's text content
     * @return  a tag definition
     */
    public static <S> TagDefinition<S> h4(String text) {
        return h4(text(text));
    }

    /**
     * A HTML {@literal <h5>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> h5(DocumentPartDefinition<S>... children) {
        return tag("h5", children);
    }

    /**
     * A HTML {@literal <h5>} element of a HTML document.
     * @param text the element's text content
     * @return  a tag definition
     */
    public static <S> TagDefinition<S> h5(String text) {
        return h5(text(text));
    }

    /**
     * A HTML {@literal <h6>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> h6(DocumentPartDefinition<S>... children) {
        return tag("h6", children);
    }

    /**
     * A HTML {@literal <h6>} element of a HTML document.
     * @param text the element's text content
     * @return  a tag definition
     */
    public static <S> TagDefinition<S> h6(String text) {
        return h6(text(text));
    }

    /**
     * A HTML {@literal <div>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> div(DocumentPartDefinition<S>... children) {
        return tag("div", children);
    }

    /**
     * A HTML {@literal <div>} element of a HTML document.
     * @param text text content
     * @return a tag definition
     */
    public static <S> TagDefinition<S> div(String text) {
        return div(text(text));
    }

    /**
     * A HTML {@literal <a>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> a(DocumentPartDefinition<S>... children) {
        return tag("a", children);
    }

    /**
     * A HTML {@literal <a>}, or anchor element of a HTML document.
     * @param href the URL that the hyperlink points to
     * @param text the link's destination text content
     * @param children other descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> a(String href, String text, DocumentPartDefinition<S>... children) {
        return a(ArrayUtils.concat(new DocumentPartDefinition[]{ attr("href", href), text(text)}, children));
    }

    /**
     * A HTML {@literal <p>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> p(DocumentPartDefinition<S>... children) {
        return tag("p", children);
    }

    /**
     * A HTML {@literal <p>} element of a HTML document.
     * @param text text content
     * @return a tag definition
     */
    public static <S> TagDefinition<S> p(String text) {
        return p(text(text));
    }

    /**
     * A HTML {@literal <span>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> span(DocumentPartDefinition<S>... children) {
        return tag("span", children);
    }

    /**
     * A HTML {@literal <span>} element of a HTML document.
     * @param text text content
     * @return a tag definition
     */
    public static <S> TagDefinition<S> span(String text) {
        return span(text(text));
    }

    /**
     * A HTML {@literal <form>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> form(DocumentPartDefinition<S>... children) {
        return tag("form", children);
    }

    /**
     * A HTML {@literal <input>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> input(DocumentPartDefinition<S>... children) {
        return tag("input", children);
    }

    /**
     * A HTML {@literal <button>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> button(DocumentPartDefinition<S>... children) {
        return tag("button", children);
    }

    /**
     * A HTML {@literal <ul>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> ul(DocumentPartDefinition<S>... children) {
        return tag("ul", children);
    }

    /**
     * A HTML {@literal <ol>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> ol(DocumentPartDefinition<S>... children) {
        return tag("ol", children);
    }

    /**
     * A HTML {@literal <li>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> li(DocumentPartDefinition<S>... children) {
        return tag("li", children);
    }

    /**
     * A HTML {@literal <li>} element of a HTML document.
     * @param text text content
     * @return a tag definition
     */
    public static <S> TagDefinition<S> li(String text) {
        return li(text(text));
    }

    /**
     * A HTML {@literal <table>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> table(DocumentPartDefinition<S>... children) {
        return tag("table", children);
    }

    /**
     * A HTML {@literal <thead>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> thead(DocumentPartDefinition<S>... children) {
        return tag("thead", children);
    }

    /**
     * A HTML {@literal <tbody>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> tbody(DocumentPartDefinition<S>... children) {
        return tag("tbody", children);
    }

    /**
     * A HTML {@literal <th>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> th(DocumentPartDefinition<S>... children) {
        return tag("th", children);
    }

    /**
     * A HTML {@literal <th>} element of a HTML document.
     * @param text text content
     * @return a tag definition
     */
    public static <S> TagDefinition<S> th(String text) {
        return th(text(text));
    }

    /**
     * A HTML {@literal <tr>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> tr(DocumentPartDefinition<S>... children) {
        return tag("tr", children);
    }

    /**
     * A HTML {@literal <td>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> td(DocumentPartDefinition<S>... children) {
        return tag("td", children);
    }

    /**
     * A HTML {@literal <td>} element of a HTML document.
     * @param text text content
     * @return a tag definition
     */
    public static <S> TagDefinition<S> td(String text) {
        return td(text(text));
    }

    /**
     * A HTML {@literal <label>} element of a HTML document.
     * @param children descendants definitions of this element
     * @return a tag definition
     */
    @SafeVarargs
    public static <S> TagDefinition<S> label(DocumentPartDefinition<S>... children) {
        return tag("label", children);
    }


    public static <S> TagDefinition<S> br() {
        return tag("br");
    }


    /**
     * Inserts a zero or more definitions provided as a stream.
     * @param items a {@link Stream} of definitions
     * @return a document part definition representing a sequence of definitions
     */
    public static <S> SequenceDefinition<S> of(Stream<DocumentPartDefinition<S>> items) {
        return new SequenceDefinition(items.toArray(DocumentPartDefinition[]::new));
    }

    /**
     * Inserts a definition which is a result of some code execution.
     * This functions allows to mix declarative DOM tree definitions and code fragments.
     * @param itemSupplier a code block
     * @return a result definition
     */
    public static <S> SequenceDefinition<S> of(Supplier<DocumentPartDefinition<S>> itemSupplier) {
        return new SequenceDefinition(new DocumentPartDefinition[] { itemSupplier.get() });
    }

    /**
     * Inserts a definition which is a result of a {@link CompletableFuture} completion.
     * @param completableFutureDefinition an asynchronous computation of a definition
     * @return a result definition
     */
    public static <S> DocumentPartDefinition<S> of(CompletableFuture<? extends DocumentPartDefinition<S>> completableFutureDefinition) {
        return completableFutureDefinition.join();
    }

    /**
     * Inserts a document part definition provided as an argument if condition is true, otherwise inserts an empty definition.
     * @param condition a condition to check
     * @param then a definition which may be inserted
     * @return a result definition
     */
    public static <S> DocumentPartDefinition<S> when(boolean condition, DocumentPartDefinition<S> then) {
        return when(condition, () -> then);
    }

    /**
     * A lazy form of conditional function.
     * Inserts a document part definition provided as in a {@link Supplier} if condition is true, otherwise inserts an empty definition.
     * @param condition a condition to check
     * @param then a {@link Supplier} of a definition which may be inserted
     * @return a result definition
     */
    public static <S> DocumentPartDefinition<S> when(boolean condition, Supplier<DocumentPartDefinition<S>> then) {
        return condition ? then.get() : EmptyDefinition.INSTANCE;
    }

    /**
     * Provides a definition of a browsers' window object.
     * @return a window object definition
     */
    public static <S> WindowRef<S> window() {
        return new WindowRef<>();
    }

    /**
     * Creates reference to a HTML element which can be used as a key for obtaining its element's properties values.
     * @see EventContext#props(ElementRef)
     * @return a reference object
     */
    public static ElementRefDefinition createElementRef() {
        return new ElementRefDefinition();
    }

    /**
     * Creates a reference to a schedule's timer.
     * @see EventContext#schedule(Consumer, TimerRef, int, TimeUnit)
     * @see EventContext#scheduleAtFixedRate(Consumer, int, int, TimeUnit)
     * @return a reference object
     */
    public static TimerRef createTimerRef() {
        return new TimerRefDefinition();
    }

    public static <S1, S2> ComponentDefinition<S1, S2> component(Render<S2> component, S2 state, Function<S2, S1> stateFun) {
        return new ComponentDefinition<S1, S2>(component, state, (Function<Object, Object>) stateFun);
    }

    public static <S> RenderingComponentDefinition<S> component(Render<S> component, S state) {
        return new RenderingComponentDefinition<S>(component, state);
    }

    private static boolean isPropertyByDefault(String name) {
        return DEFAULT_PROPERTIES_NAMES.contains(name);
    }

    private static Map<String, String> merge(Map<String, String> m1, Map<String, String> m2) {
        final Map<String, String> result = new HashMap<>(m1);
        result.putAll(m2);
        return result;
    }
}
