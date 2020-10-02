package rsp.dom;

import rsp.ChangesPerformer;
import rsp.XmlNs;

import java.util.List;

public class Diff {

    private final Tag current;
    private final Tag work;
    private final ChangesPerformer performer;

    public Diff(Tag lhsRoot, Tag rhsRoot, ChangesPerformer performer) {
        this.current = lhsRoot;
        this.work = rhsRoot;
        this.performer = performer;
    }

    public static void diff(Tag c, Tag w, Path path, ChangesPerformer changesPerformer) {
        if (!c.name.equals(w.name)) {
            changesPerformer.remove(path);
            create((Tag)w, path, changesPerformer);
        } else {
            diffStyles(c.styles, w.styles, path, changesPerformer);
            diffAttributes(c.attributes, w.attributes, path, changesPerformer);
            diffChildren(c.children, w.children, path.incLevel(), changesPerformer);
        }
    }

    private static void diffAttributes(List<Attribute> ca, List<Attribute> wa, Path path, ChangesPerformer performer) {
        var c = ca.listIterator();
        var w = wa.listIterator();
        while(c.hasNext() || w.hasNext()) {
            if (c.hasNext() && w.hasNext()) {
                final Attribute cAttr = c.next();
                final Attribute wAttr = w.next();
                if (!cAttr.equals(wAttr)) {
                    performer.removeAttr(path, XmlNs.html, cAttr.name);
                    performer.setAttr(path, XmlNs.html, wAttr.name, wAttr.value);
                }
            } else if (c.hasNext()) {
                final Attribute cAttr = c.next();
                performer.removeAttr(path, XmlNs.html, cAttr.name);
            } else {
                final Attribute wAttr = w.next();
                performer.setAttr(path, XmlNs.html, wAttr.name, wAttr.value);
            }
        }
    }

    private static void diffStyles(List<Style> ca, List<Style> wa, Path path, ChangesPerformer performer) {
        var c = ca.listIterator();
        var w = wa.listIterator();
        while(c.hasNext() || w.hasNext()) {
            if (c.hasNext() && w.hasNext()) {
                final Style cAttr = c.next();
                final Style wAttr = w.next();
                if (!cAttr.equals(wAttr)) {
                    performer.removeStyle(path, cAttr.name);
                    performer.setStyle(path, wAttr.name, wAttr.value);
                }
            } else if (c.hasNext()) {
                final Style cAttr = c.next();
                performer.removeStyle(path, cAttr.name);
            } else {
                final Style wAttr = w.next();
                performer.setStyle(path, wAttr.name, wAttr.value);
            }
        }
    }

    private static void diffChildren(List<Node> cc, List<Node> wc, Path path, ChangesPerformer performer) {
        var c = cc.listIterator();
        var w = wc.listIterator();

        var p = path;
        while(c.hasNext() || w.hasNext()) {
            if (c.hasNext() && w.hasNext()) {
                final Node nc = c.next();
                final Node nw = w.next();
                if (nc instanceof Tag && nw instanceof Tag) {
                    diff((Tag)nc, (Tag)nw, p, performer);
                } else if (nw instanceof Tag) {
                    performer.remove(path);
                    create(((Tag) nw), path, performer);
                } else if (nc instanceof Tag) {
                    performer.remove(path);
                    performer.createText(path.parent().get(), path, ((Text)nw).text);
                } else if (!((Text)nc).text.equals(((Text)nw).text)) {
                    performer.createText(path.parent().get(), path, ((Text)nw).text);
                }
            } else if (c.hasNext()) {
                c.next();
                performer.remove(path);
            } else {
                final Node nw = w.next();
                create((Tag)nw, p, performer);
            }
            p = p.incSibling();
        }

    }

    private static void create(Tag tag, Path path, ChangesPerformer changesPerformer) {
        changesPerformer.create(path, tag.xmlns, tag.name);
        var p = path.incLevel();
        for(Node child:tag.children) {
            if (child instanceof Tag) {
                final Tag newTag = (Tag) child;
                create(newTag, p, changesPerformer);
                for(Style style: newTag.styles) {
                    changesPerformer.setStyle(p, style.name, style.value);
                }
                for(Attribute attribute: newTag.attributes) {
                    changesPerformer.setAttr(p, XmlNs.html, attribute.name, attribute.value);
                }
            } else if (child instanceof Text) {
                final Text text = (Text) child;
                changesPerformer.createText(tag.path, p, text.text);
            }
            p = p.incSibling();
        }
    }

    public void run() {
        diff(current, work, new Path(1), performer);
    }
}
