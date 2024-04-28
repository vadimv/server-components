package rsp.dom;

public final class Text implements Node {

    public final String text;

    public Text(final String text) {
        this.text = text;
    }

    @Override
    public void appendString(final StringBuilder sb) {
        sb.append(text);
    }

}
