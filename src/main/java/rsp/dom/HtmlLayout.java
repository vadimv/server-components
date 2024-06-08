package rsp.dom;

public sealed interface HtmlLayout {
    HtmlLayout INLINE = new Inline();
    HtmlLayout PAD_2 = new WithIndent(2);
    HtmlLayout PAD_4 = new WithIndent(4);

    record Inline() implements HtmlLayout {
    }

    record WithIndent(int indentSpaces) implements HtmlLayout {
    }
}
