package rsp.dom;

public record XmlNs(String name, String uri) {

    public static final XmlNs xlink = new XmlNs("xlink", "http://www.w3.org/1999/xlink");
    public static final XmlNs html = new XmlNs("html", "http://www.w3.org/1999/xhtml");
    public static final XmlNs svg = new XmlNs("svg", "http://www.w3.org/2000/svg");
    public static final XmlNs mathml = new XmlNs("mathml", "http://www.w3.org/1998/Math/MathML");

    @Override
    public String toString() {
        return name;
    }

}
