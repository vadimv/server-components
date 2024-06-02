package rsp.dom;

import java.util.*;
import java.util.concurrent.CopyOnWriteArraySet;

public final class Diff {

    public static void diff(final Tag ct,
                            final Tag wt,
                            final TreePositionPath path,
                            final DomChangesContext changesPerformer,
                            final HtmlBuilder hb) {
        Objects.requireNonNull(ct);
        Objects.requireNonNull(wt);
        Objects.requireNonNull(changesPerformer);
        if (!ct.name.equals(wt.name)) {
            changesPerformer.removeNode(path.parent(), path);
            createTag(wt, path, changesPerformer, hb);
        } else {
            diffStyles(ct.styles, wt.styles, path, changesPerformer);
            diffAttributes(ct.attributes, wt.attributes, path, changesPerformer);
            diffChildren(ct.children, wt.children, path.incLevel(), changesPerformer, hb);
        }
    }

    public static void diffChildren(final List<? extends Node> cc,
                                    final List<? extends Node> wc,
                                    final TreePositionPath parentTagPath,
                                    final DomChangesContext performer,
                                    final HtmlBuilder hb) {
        final ListIterator<? extends Node> cci = cc.listIterator();
        final ListIterator<? extends Node> wci = wc.listIterator();
        TreePositionPath p = parentTagPath;
        while(cci.hasNext() || wci.hasNext()) {
            if (cci.hasNext() && wci.hasNext()) {
                final Node cn = cci.next();
                final Node wn = wci.next();
                if (cn instanceof Tag ct && wn instanceof Tag wt) {
                    diff(ct, wt, p, performer, hb);
                } else if (wn instanceof Tag t) {
                    performer.removeNode(p.parent(), p);
                    createTag(t, parentTagPath, performer, hb);
                } else if (cn instanceof Tag) {
                    performer.removeNode(p.parent(), p);
                    hb.reset();
                    hb.buildHtml(wn);
                    performer.createText(parentTagPath.parent(), parentTagPath, hb.toString());
                } else {
                    hb.reset();
                    hb.buildHtml(cn);
                    final String ncText = hb.toString();
                    hb.reset();
                    hb.buildHtml(wn);
                    final String nwText = hb.toString();
                    if (!ncText.equals(nwText)) {
                        performer.createText(p.parent(), p, nwText);
                    }
                }
            } else if (cci.hasNext()) {
                cci.next();
                performer.removeNode(p.parent(), p);
            } else {
                final Node wn = wci.next();
                if (wn instanceof Tag t) {
                    createTag(t, p, performer, hb);
                } else {
                    hb.reset();
                    hb.buildHtml(wn);
                    performer.createText(p.parent(), p, hb.toString());
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
                                  final HtmlBuilder hb) {
        changesPerformer.createTag(path, tag.xmlns, tag.name);
        for (final Style style: tag.styles) {
            changesPerformer.setStyle(path, style.name, style.value);
        }
        for (final Attribute attribute: tag.attributes) {
            changesPerformer.setAttr(path, XmlNs.html, attribute.name, attribute.value, attribute.isProperty);
        }
        TreePositionPath p = path.incLevel();
        for (final Node child:tag.children) {
            if (child instanceof Tag t) {
                createTag(t, p, changesPerformer, hb);
            } else if (child instanceof Text) {
                hb.reset();
                hb.buildHtml(child);
                changesPerformer.createText(path, p, hb.toString());
                p = p.incSibling();
            }
            p = p.incSibling();
        }
    }
}
