package rsp.dsl;

import rsp.dom.Event;
import rsp.page.EventContext;
import rsp.util.ArrayUtils;

import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

/**
 * A HTML tags definitions and related util functions DSL
 */
public final class Html {

    private Html() {}

    // TODO verify all this property names
    /**
     * Attributes names which are interpreted by default as properties
     */
    public final static String DEFAULT_PROPERTIES_NAMES =
        "autofocus, autoplay, async, checked, controls, defer, disabled, hidden, loop, multiple, open, readonly, required, scoped, selected, value";

    /**
     * An arbitrary HTML element
     * @param name an element name
     * @param children descendant nodes definitions of this element
     * @return a tag definition
     */
    public static TagDefinition tag(String name, DocumentPartDefinition... children) {
        return new TagDefinition(name, children);
    }

    /**
     * A HTML element attribute
     * @param name an attribute name
     * @param value an attribute value
     * @param isProperty true if this attribute should be interpreted as a property, false otherwise
     * @return an attribute definition
     */
    public static AttributeDefinition attr(String name, String value, boolean isProperty) {
        return new AttributeDefinition(name, value, isProperty);
    }

    /**
     * A HTML element attribute
     * This attribute is interpreted as a property if its name is one of the properties by default:
     * {@value #DEFAULT_PROPERTIES_NAMES}
     * {@link #DEFAULT_PROPERTIES_NAMES}
     * @param name an attribute name
     * @param value an attribute value
     * @return an attribute definition
     */
    public static AttributeDefinition attr(String name, String value) {
        return attr(name, value, isPropertyByDefault(name));
    }

    /**
     * A boolean attribute
     * @param name an attribute name
     * @return an attribute definition
     */
    public static AttributeDefinition attr(String name) {
        return new AttributeDefinition(name, name, isPropertyByDefault(name));
    }

    /**
     * An element's text content
     * @param text a text
     * @return a text node definition
     */
    public static TextDefinition text(String text) {
        return new TextDefinition(text);
    }

    /**
     * An element's text content
     * @param obj an arbitrary object to be converted to text using its {@link #toString()} method
     * @return a text node definition
     */
    public static TextDefinition text(Object obj) {
        return new TextDefinition(obj.toString());
    }

    /**
     * A HTML {@literal <html>} element, the root element of a HTML document
     * @param children descendant nodes definitions of this element
     * @return a tag definition
     */
    public static TagDefinition html(DocumentPartDefinition... children) {
        return tag("html", children);
    }

    /**
     * A HTML {@literal <body>} element of a HTML document
     * @param children descendant nodes definitions of this element
     * @return a tag definition
     */
    public static  TagDefinition body(DocumentPartDefinition... children) {
        return tag("body", children);
    }

    /**
     * A HTML {@literal <head>} element of a HTML document
     * @param children descendant nodes definitions of this element
     * @return a tag definition
     */
    public static  TagDefinition head(DocumentPartDefinition... children) {
        return tag("head", children);
    }

    /**
     * A HTML {@literal <title>} element of a HTML document
     * @param text a document's title text
     * @return a tag definition
     */
    public static  TagDefinition title(String text) {
        return tag("title", text(text));
    }

    /**
     * A HTML {@literal <link>} element of a HTML document
     * @param children the element's attributes
     * @return a tag definition
     */
    public static TagDefinition link(AttributeDefinition... children) {
        return tag("link", children);
    }

    /**
     * A HTML {@literal <meta>} element of a HTML document
     * @param children the element's attributes
     * @return a tag definition
     */
    public static TagDefinition meta(AttributeDefinition... children) {
        return tag("meta", children);
    }

    /**
     * A HTML {@literal <h1>} element of a HTML document
     * @param children descendant nodes definitions of this element
     * @return  a tag definition
     */
    public static TagDefinition h1(DocumentPartDefinition... children) {
        return tag("h1", children);
    }

    /**
     * A HTML {@literal <h2>} element of a HTML document
     * @param children descendant nodes definitions of this element
     * @return  a tag definition
     */
    public static TagDefinition h2(DocumentPartDefinition... children) {
        return tag("h2", children);
    }

    /**
     * A HTML {@literal <h3>} element of a HTML document
     * @param children descendant nodes definitions of this element
     * @return  a tag definition
     */
    public static TagDefinition h3(DocumentPartDefinition... children) {
        return tag("h3", children);
    }

    /**
     * A HTML {@literal <h4>} element of a HTML document
     * @param children descendant nodes definitions of this element
     * @return  a tag definition
     */
    public static TagDefinition h4(DocumentPartDefinition... children) {
        return tag("h4", children);
    }

    /**
     * A HTML {@literal <h5>} element of a HTML document
     * @param children descendant nodes definitions of this element
     * @return  a tag definition
     */
    public static TagDefinition h5(DocumentPartDefinition... children) {
        return tag("h5", children);
    }

    /**
     * A HTML {@literal <h6>} element of a HTML document
     * @param children descendant nodes definitions of this element
     * @return  a tag definition
     */
    public static TagDefinition h6(DocumentPartDefinition... children) {
        return tag("h6", children);
    }

    /**
     * A HTML {@literal <div>} element of a HTML document
     * @param children descendant nodes definitions of this element
     * @return  a tag definition
     */
    public static TagDefinition div(DocumentPartDefinition... children) {
        return tag("div", children);
    }

    /**
     * A HTML {@literal <div>} element of a HTML document
     * @param text text content
     * @return  a tag definition
     */
    public static TagDefinition div(String text) {
        return div(text(text));
    }

    /**
     * A HTML {@literal <a>} element of a HTML document
     * @param children descendant nodes definitions of this element
     * @return a tag definition
     */    public static TagDefinition a(DocumentPartDefinition... children) {
        return tag("a", children);
    }

    /**
     * A HTML {@literal <a>}, or anchor element of a HTML document
     * @param href the URL that the hyperlink points to
     * @param text the link's destination text content
     * @param children other descendant nodes definitions of this element
     * @return a tag definition
     */
    public static TagDefinition a(String href, String text, DocumentPartDefinition... children) {
        return a(ArrayUtils.concat(new DocumentPartDefinition[]{ attr("href", href), text(text)}, children));
    }

    /**
     * A HTML {@literal <p>} element of a HTML document
     * @param children descendant nodes definitions of this element
     * @return a tag definition
     */
    public static TagDefinition p(DocumentPartDefinition... children) {
        return tag("p", children);
    }

    public static TagDefinition p(String text) {
        return p(text(text));
    }

    public static TagDefinition span(DocumentPartDefinition... children) {
        return tag("span", children);
    }

    public static TagDefinition span(String text) {
        return span(text(text));
    }

    public static TagDefinition form(DocumentPartDefinition... children) {
        return tag("form", children);
    }

    public static TagDefinition input(DocumentPartDefinition... children) {
        return tag("input", children);
    }

    public static TagDefinition button(DocumentPartDefinition... children) {
        return tag("button", children);
    }

    public static TagDefinition ul(DocumentPartDefinition... children) {
        return tag("ul", children);
    }

    public static TagDefinition ol(DocumentPartDefinition... children) {
        return tag("ol", children);
    }

    public static TagDefinition li(DocumentPartDefinition... children) {
        return tag("li", children);
    }

    public static TagDefinition li(String text) {
        return li(text(text));
    }

    public static TagDefinition table(DocumentPartDefinition... children) {
        return tag("table", children);
    }

    public static TagDefinition thead(DocumentPartDefinition... children) {
        return tag("thead", children);
    }

    public static TagDefinition tbody(DocumentPartDefinition... children) {
        return tag("tbody", children);
    }

    public static TagDefinition th(DocumentPartDefinition... children) {
        return tag("th", children);
    }

    public static TagDefinition th(String text) {
        return th(text(text));
    }

    public static TagDefinition tr(DocumentPartDefinition... children) {
        return tag("tr", children);
    }

    public static TagDefinition td(DocumentPartDefinition... children) {
        return tag("td", children);
    }

    public static TagDefinition td(String text) {
        return td(text(text));
    }

    public static TagDefinition label(DocumentPartDefinition... children) {
        return tag("label", children);
    }

    private static boolean isPropertyByDefault(String name) {
        return DEFAULT_PROPERTIES_NAMES.contains(name);
    }

    public static AttributeDefinition prop(String name, String value) {
        return attr(name, value, true);
    }

    public static StyleDefinition style(String name, String value) {
        return new StyleDefinition(name, value);
    }

    public static SequenceDefinition of(Stream<DocumentPartDefinition> items) {
        return new SequenceDefinition(items.toArray(DocumentPartDefinition[]::new));
    }

    public static SequenceDefinition of(Supplier<DocumentPartDefinition> itemSupplier) {
        return new SequenceDefinition(new DocumentPartDefinition[] { itemSupplier.get() });
    }

    public static DocumentPartDefinition of(CompletableFuture<? extends DocumentPartDefinition> itemSupplier) {
        return itemSupplier.join();
    }

    public static DocumentPartDefinition when(boolean condition, DocumentPartDefinition then) {
        return when(condition, () -> then);
    }

    public static DocumentPartDefinition when(boolean condition, Supplier<DocumentPartDefinition> then) {
        return condition ? then.get() : new EmptyDefinition();
    }

    public static EventDefinition on(String eventType, Consumer<EventContext> handler) {
        return new EventDefinition(eventType, handler, Event.NO_MODIFIER);
    }

    public static WindowDefinition window() {
        return new WindowDefinition();
    }

    public static RefDefinition createRef() {
        return new RefDefinition();
    }
}
