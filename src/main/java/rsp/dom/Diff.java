package rsp.dom;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

public final class Diff {
    private final Optional<Tag> current;
    private final Tag work;
    private final DomChangesContext performer;

    public Diff(final Optional<Tag> current, final Tag work, final DomChangesContext performer) {
        this.current = Objects.requireNonNull(current);
        this.work = Objects.requireNonNull(work);
        current.ifPresent(c -> {
            if (!c.path.equals(work.path)) {
                throw new IllegalArgumentException("Root paths for a diff expected to be equal");
            }
        });
        this.performer = Objects.requireNonNull(performer);
    }

    public void run() {
        current.ifPresentOrElse(c -> diff(c, work, c.path, performer),
                                () -> create(work, work.path, performer));
    }

    private static void diff(final Tag c, final Tag w, final VirtualDomPath path, final DomChangesContext changesPerformer) {
        if (!c.name.equals(w.name)) {
            changesPerformer.remove(path.parent().get(), path);
            create(w, path, changesPerformer);
        } else {
            diffStyles(c.styles, w.styles, path, changesPerformer);
            diffAttributes(c.attributes, w.attributes, path, changesPerformer);
            diffChildren(c.children, w.children, path.incLevel(), changesPerformer);
        }
    }

    private static void diffAttributes(final CopyOnWriteArraySet<Attribute> ca,
                                       final CopyOnWriteArraySet<Attribute> wa,
                                       final VirtualDomPath path,
                                       final DomChangesContext performer) {
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

    private static void diffStyles(final CopyOnWriteArraySet<Style> ca,
                                   final CopyOnWriteArraySet<Style> wa,
                                   final VirtualDomPath path,
                                   final DomChangesContext performer) {
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

    private static void diffChildren(final List<Node> cc, final List<Node> wc, final VirtualDomPath path, final DomChangesContext performer) {
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

    private static void create(final Tag tag, final VirtualDomPath path, final DomChangesContext changesPerformer) {
        changesPerformer.create(path, tag.xmlns, tag.name);
        for (final Style style: tag.styles) {
            changesPerformer.setStyle(path, style.name, style.value);
        }
        for (final Attribute attribute: tag.attributes) {
            changesPerformer.setAttr(path, XmlNs.html, attribute.name, attribute.value, attribute.isProperty);
        }
        VirtualDomPath p = path.incLevel();
        for (final Node child:tag.children) {
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
