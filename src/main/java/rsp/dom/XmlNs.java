package rsp.dom;

import java.util.Objects;

public final class XmlNs {

    public static final XmlNs xlink = new XmlNs("xlink", "http://www.w3.org/1999/xlink");
    public static final XmlNs html = new XmlNs("html", "http://www.w3.org/1999/xhtml");
    public static final XmlNs svg = new XmlNs("svg", "http://www.w3.org/2000/svg");
    public static final XmlNs mathml = new XmlNs("mathml", "http://www.w3.org/1998/Math/MathML");

    public final String name;
    public final String uri;

    public XmlNs(final String name, final String uri) {
        this.name = name;
        this.uri = uri;
    }

    @Override
    public String toString() {
        return name;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        final XmlNs xmlNs = (XmlNs) o;
        return Objects.equals(name, xmlNs.name) &&
                Objects.equals(uri, xmlNs.uri);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, uri);
    }
}
