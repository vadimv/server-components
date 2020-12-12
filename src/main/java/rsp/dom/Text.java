package rsp.dom;

public final class Text implements Node {
    public final VirtualDomPath path;
    public final String text;

    public Text(VirtualDomPath path, String text) {
        this.path = path;
        this.text = text;
    }

    @Override
    public VirtualDomPath path() {
        return path;
    }


    @Override
    public void appendString(StringBuilder sb) {
        sb.append(text);
    }

}
