package rsp.dsl;

import rsp.Event;
import rsp.EventContext;

import java.util.function.Consumer;
import java.util.function.Supplier;
import java.util.stream.Stream;

public class Html {

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

    public static  TagDefinition title(String title) {
        return tag("title", text(title));
    }

    public static TagDefinition link(AttributeDefinition... children) {
        return tag("link", children);
    }

    public static TagDefinition meta(AttributeDefinition... children) {
        return tag("meta", children);
    }

    public static TagDefinition div(DocumentPartDefinition... children) {
        return tag("div", children);
    }

    public static TagDefinition span(DocumentPartDefinition... children) {
        return tag("span", children);
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

    public static TextDefinition text(String text) {
        return new TextDefinition(text);
    }

    public static TextDefinition text(Object text) {
        return new TextDefinition(text.toString());
    }

    public static AttributeDefinition attr(String name, String value) {
        return new AttributeDefinition(name, value);
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

    public static DocumentPartDefinition when(boolean condition, DocumentPartDefinition then) {
        return condition ? then : new EmptyDefinition();
    }

    public static EventDefinition event(String eventType, Consumer<EventContext> handler) {
        return new EventDefinition(eventType, handler, Event.NO_MODIFIER);
    }

    public static WindowDefinition window() {
        return new WindowDefinition();
    }

    public static RefDefinition createRef() {
        return new RefDefinition();
    }
}
