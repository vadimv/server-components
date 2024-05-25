package rsp.dom;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

public final class Diff {

    public static void diff(final Tag c,
                            final Tag w,
                            final TreePositionPath path,
                            final DomChangesContext changesPerformer,
                            final StringBuilder sb) {
        Objects.requireNonNull(c);
        Objects.requireNonNull(w);
        Objects.requireNonNull(changesPerformer);
        if (!c.name.equals(w.name)) {
            changesPerformer.removeNode(path.parent(), path);
            createTag(w, path, changesPerformer, sb);
        } else {
            diffStyles(c.styles, w.styles, path, changesPerformer);
            diffAttributes(c.attributes, w.attributes, path, changesPerformer);
            diffChildren(c.children, w.children, path.incLevel(), changesPerformer, sb);
        }
    }

    public static void diffChildren(final List<? extends Node> cc,
                                    final List<? extends Node> wc,
                                    final TreePositionPath parentTagPath,
                                    final DomChangesContext performer,
                                    final StringBuilder sb) {
        final ListIterator<? extends Node> c = cc.listIterator();
        final ListIterator<? extends Node> w = wc.listIterator();
        TreePositionPath p = parentTagPath;
        while(c.hasNext() || w.hasNext()) {
            if (c.hasNext() && w.hasNext()) {
                final Node nc = c.next();
                final Node nw = w.next();
                if (nc instanceof Tag && nw instanceof Tag) {
                    diff((Tag)nc, (Tag)nw, p, performer, sb);
                } else if (nw instanceof Tag) {
                    performer.removeNode(p.parent(), p);
                    createTag(((Tag) nw), parentTagPath, performer, sb);
                } else if (nc instanceof Tag) {
                    performer.removeNode(p.parent(), p);
                    sb.setLength(0);
                    nw.appendString(sb);
                    performer.createText(parentTagPath.parent(), parentTagPath, sb.toString());
                } else {
                    sb.setLength(0);
                    nc.appendString(sb);
                    final String ncText = sb.toString();
                    sb.setLength(0);
                    nw.appendString(sb);
                    final String nwText = sb.toString();
                    if (!ncText.equals(nwText)) {
                        performer.createText(p.parent(), p, nwText);
                    }
                }
            } else if (c.hasNext()) {
                c.next();
                performer.removeNode(p.parent(), p);
            } else {
                final Node nw = w.next();
                if (nw instanceof Tag) {
                    createTag((Tag)nw, p, performer, sb);
                } else {
                    sb.setLength(0);
                    nw.appendString(sb);
                    performer.createText(p.parent(), p, sb.toString());
                }
            }
            if (p.level() > 0) p = p.incSibling();
        }
    }

    private static void diffAttributes(final CopyOnWriteArraySet<Attribute> ca,
                                       final CopyOnWriteArraySet<Attribute> wa,
                                       final TreePositionPath path,
                                       final DomChangesContext performer) {
        final Set<Attribute> c = new CopyOnWriteArraySet<>(ca);
        final Set<Attribute> w = new CopyOnWriteArraySet<>(wa);
        c.removeAll(wa);
        c.forEach(attribute -> performer.removeAttr(path, XmlNs.html, attribute.name, attribute.isProperty));
        w.removeAll(ca);
        w.forEach(attribute -> performer.setAttr(path, XmlNs.html, attribute.name, attribute.value, attribute.isProperty));
    }

    private static void diffStyles(final CopyOnWriteArraySet<Style> ca,
                                   final CopyOnWriteArraySet<Style> wa,
                                   final TreePositionPath path,
                                   final DomChangesContext performer) {
        final Set<Style> c = new CopyOnWriteArraySet<>(ca);
        final Set<Style> w = new CopyOnWriteArraySet<>(wa);
        c.removeAll(wa);
        c.forEach(attribute -> performer.removeStyle(path, attribute.name));
        w.removeAll(ca);
        w.forEach(attribute -> performer.setStyle(path, attribute.name, attribute.value));
    }

    private static void createTag(final Tag tag,
                                  final TreePositionPath path,
                                  final DomChangesContext changesPerformer,
                                  final StringBuilder sb) {
        changesPerformer.createTag(path, tag.xmlns, tag.name);
        for (final Style style: tag.styles) {
            changesPerformer.setStyle(path, style.name, style.value);
        }
        for (final Attribute attribute: tag.attributes) {
            changesPerformer.setAttr(path, XmlNs.html, attribute.name, attribute.value, attribute.isProperty);
        }
        TreePositionPath p = path.incLevel();
        for (final Node child:tag.children) {
            if (child instanceof Tag) {
                final Tag newTag = (Tag) child;
                createTag(newTag, p, changesPerformer, sb);
            } else if (child instanceof Text) {
                sb.setLength(0);
                child.appendString(sb);
                changesPerformer.createText(path, p, sb.toString());
                p = p.incSibling();
            }
            p = p.incSibling();
        }
    }
}
