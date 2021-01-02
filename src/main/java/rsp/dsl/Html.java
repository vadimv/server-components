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
 * A HTML tags definitions DSL
 */
public final class Html {

    // TODO verify all this property names
    private final static String[] DEFAULT_PROPERTIES_NAMES = {
            "autofocus", "autoplay", "async", "checked", "controls", "defer", "disabled", "hidden", "loop",
            "multiple", "open", "readonly", "required", "scoped", "selected", "value"
    };

    private static TagDefinition tag(String name, DocumentPartDefinition... children) {
        return new TagDefinition(name, children);
    }

    public static TagDefinition html(DocumentPartDefinition... children) {
        return tag("html", children);
    }

    public static  TagDefinition body(DocumentPartDefinition... children) {
        return tag("body", children);
    }

    public static  TagDefinition head(DocumentPartDefinition... children) {
        return tag("head", children);
    }

    public static  TagDefinition title(String text) {
        return tag("title", text(text));
    }

    public static TagDefinition link(AttributeDefinition... children) {
        return tag("link", children);
    }

    public static TagDefinition meta(AttributeDefinition... children) {
        return tag("meta", children);
    }

    public static TagDefinition h1(DocumentPartDefinition... children) {
        return tag("h1", children);
    }

    public static TagDefinition h2(DocumentPartDefinition... children) {
        return tag("h2", children);
    }

    public static TagDefinition h3(DocumentPartDefinition... children) {
        return tag("h3", children);
    }

    public static TagDefinition h4(DocumentPartDefinition... children) {
        return tag("h4", children);
    }

    public static TagDefinition h5(DocumentPartDefinition... children) {
        return tag("h5", children);
    }

    public static TagDefinition h6(DocumentPartDefinition... children) {
        return tag("h6", children);
    }

    public static TagDefinition div(DocumentPartDefinition... children) {
        return tag("div", children);
    }

    public static TagDefinition div(String text) {
        return div(text(text));
    }

    public static TagDefinition a(DocumentPartDefinition... children) {
        return tag("a", children);
    }

    public static TagDefinition a(String href, String text, DocumentPartDefinition... children) {
        return a(ArrayUtils.concat(new DocumentPartDefinition[]{ attr("href", href), text(text)}, children));
    }

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

    public static TextDefinition text(String text) {
        return new TextDefinition(text);
    }

    public static TextDefinition text(Object obj) {
        return new TextDefinition(obj.toString());
    }


    public static TagDefinition label(DocumentPartDefinition... children) {
        return tag("label", children);
    }


    public static AttributeDefinition attr(String name, String value, boolean isProperty) {
        return new AttributeDefinition(name, value, isProperty);
    }

    public static AttributeDefinition attr(String name, String value) {
        return attr(name, value, isPropertyByDefault(name));
    }

    public static AttributeDefinition attr(String name) {
        return new AttributeDefinition(name, name, isPropertyByDefault(name));
    }

    private static boolean isPropertyByDefault(String name) {
        return Arrays.asList(DEFAULT_PROPERTIES_NAMES).contains(name);
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
