package rsp;

public class TextPrettyPrinting {
    public static final TextPrettyPrinting DEFAULT = new TextPrettyPrinting(' ', 2, "\n", true);
    public static final TextPrettyPrinting  NO_PRETTY_PRINTING = new TextPrettyPrinting(' ', 0, "", false);

    public final char indentationChar;
    public final int indentationSize;
    public final CharSequence lineBreak;
    public final boolean enableAutoIndent;

    public TextPrettyPrinting(char indentationChar, int indentationSize, CharSequence lineBreak, boolean enableAutoIndent) {
        this.indentationChar = indentationChar;
        this.indentationSize = indentationSize;
        this.lineBreak = lineBreak;
        this.enableAutoIndent = enableAutoIndent;
    }
}
