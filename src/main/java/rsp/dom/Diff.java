package rsp.dom;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

public final class Diff {
    private final Optional<Tag> current;
    private final Tag work;
    private final DomChangesPerformer performer;

    public Diff(Optional<Tag> current, Tag work, DomChangesPerformer performer) {
        this.current = Objects.requireNonNull(current);
        this.work = Objects.requireNonNull(work);
        this.performer = Objects.requireNonNull(performer);
    }

    public void run() {
        current.ifPresentOrElse(current -> diff(current, work, new VirtualDomPath(1), performer),
                                () -> create(work, new VirtualDomPath(1), performer));
    }

    private static void diff(Tag c, Tag w, VirtualDomPath path, DomChangesPerformer changesPerformer) {
        if (!c.name.equals(w.name)) {
            changesPerformer.remove(path.parent().get(), path);
            create(w, path, changesPerformer);
        } else {
            diffStyles(c.styles, w.styles, path, changesPerformer);
            diffAttributes(c.attributes, w.attributes, path, changesPerformer);
            diffChildren(c.children, w.children, path.incLevel(), changesPerformer);
        }
    }

    private static void diffAttributes(CopyOnWriteArraySet<Attribute> ca, CopyOnWriteArraySet<Attribute> wa, VirtualDomPath path, DomChangesPerformer performer) {
        final Set<Attribute> c = new CopyOnWriteArraySet<>(ca);
        final Set<Attribute> w = new CopyOnWriteArraySet<>(wa);
        c.removeAll(wa);
        c.forEach(attribute ->  {
            performer.removeAttr(path, XmlNs.html, attribute.name, attribute.isProperty);
        });
        w.removeAll(ca);
        w.forEach(attribute -> {
            performer.setAttr(path, XmlNs.html, attribute.name, attribute.value, attribute.isProperty);
        });
    }

    private static void diffStyles(CopyOnWriteArraySet<Style> ca, CopyOnWriteArraySet<Style> wa, VirtualDomPath path, DomChangesPerformer performer) {
        final Set<Style> c = new CopyOnWriteArraySet<>(ca);
        final Set<Style> w = new CopyOnWriteArraySet<>(wa);
        c.removeAll(wa);
        c.forEach(attribute ->  {
            performer.removeStyle(path, attribute.name);
        });
        w.removeAll(ca);
        w.forEach(attribute -> {
            performer.setStyle(path, attribute.name, attribute.value);
        });
    }

    private static void diffChildren(List<Node> cc, List<Node> wc, VirtualDomPath path, DomChangesPerformer performer) {
        final ListIterator<Node> c = cc.listIterator();
        final ListIterator<Node> w = wc.listIterator();

        VirtualDomPath p = path;
        while(c.hasNext() || w.hasNext()) {
            if (c.hasNext() && w.hasNext()) {
                final Node nc = c.next();
                final Node nw = w.next();
                if (nc instanceof Tag && nw instanceof Tag) {
                    diff((Tag)nc, (Tag)nw, p, performer);
                } else if (nw instanceof Tag) {
                    performer.remove(nc.path().parent().get(), nc.path());
                    create(((Tag) nw), path, performer);
                } else if (nc instanceof Tag) {
                    performer.remove(nc.path().parent().get(), nc.path());
                    performer.createText(path.parent().get(), path, ((Text)nw).text);
                } else if (!((Text)nc).text.equals(((Text)nw).text)) {
                    performer.createText(path.parent().get(), path, ((Text)nw).text);
                }
            } else if (c.hasNext()) {
                final Node nc = c.next();
                performer.remove(nc.path().parent().get(), nc.path());
            } else {
                final Node nw = w.next();
                create((Tag)nw, p, performer);
            }
            p = p.incSibling();
        }

    }

    private static void create(Tag tag, VirtualDomPath path, DomChangesPerformer changesPerformer) {
        changesPerformer.create(path, tag.xmlns, tag.name);
        for (Style style: tag.styles) {
            changesPerformer.setStyle(path, style.name, style.value);
        }
        for (Attribute attribute: tag.attributes) {
            changesPerformer.setAttr(path, XmlNs.html, attribute.name, attribute.value, attribute.isProperty);
        }
        VirtualDomPath p = path.incLevel();
        for (Node child:tag.children) {
            if (child instanceof Tag) {
                final Tag newTag = (Tag) child;
                create(newTag, p, changesPerformer);
            } else if (child instanceof Text) {
                final Text text = (Text) child;
                changesPerformer.createText(path, p, text.text);
            }
            p = p.incSibling();
        }
    }
}
