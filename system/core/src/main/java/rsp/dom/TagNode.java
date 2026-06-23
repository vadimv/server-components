package rsp.dom;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArraySet;

/**
 * Server-side virtual-DOM representation of a single HTML (or SVG) element.
 *
 * <p>A {@code TagNode} is the mutable building block of the in-memory tree that components produce
 * during rendering. {@link rsp.component.TreeBuilder} opens a {@code TagNode} for each element in
 * the DSL, attaches its {@link AttributeNode attributes} and child {@link Node nodes} (further
 * {@code TagNode}s and {@link TextNode TextNodes}), and stitches the result into the component's
 * subtree. Two snapshots of that tree — one from before the latest update, one from after — are
 * the inputs to {@link NodesTreeDiff}, which compares them and emits the {@code DomChange}
 * sequence ({@code Create}, {@code CreateText}, {@code Remove}, {@code SetAttr}, {@code RemoveAttr},
 * {@code InsertBefore}) that the client applies to keep its real DOM in sync.
 *
 * <p>Fields:
 * <ul>
 *   <li>{@link #xmlns} and {@link #name} identify the element type. A diff that detects a change
 *       in either treats it as a different element and emits a remove + recreate, not a mutation.</li>
 *   <li>{@link #isSelfClosing} controls how {@link HtmlBuilder} serializes the SSR output.</li>
 *   <li>{@link #attributes} is the live, order-preserving set of HTML attributes (using
 *       {@code CopyOnWriteArraySet} so iteration order matches insertion order for stable diff
 *       output). Diffed by set difference: removed attrs emit {@code RemoveAttr}, added attrs emit
 *       {@code SetAttr}, common attrs are skipped.</li>
 *   <li>{@link #children} is the ordered list of child nodes. Diffed positionally by default;
 *       when every child carries a non-null {@link #key()}, the keyed branch of
 *       {@link NodesTreeDiff} matches children across renders by key and uses
 *       {@code InsertBefore} for moves so retained nodes keep their identity on the client.</li>
 *   <li>{@link #key()} is the stable identity segment for keyed list diffing (see
 *       {@link rsp.dsl.Html#key}); {@code null} for unkeyed nodes (the common case).</li>
 * </ul>
 *
 * <p>This class is intentionally a mutable POJO rather than a record: {@link rsp.component.TreeBuilder}
 * incrementally builds the tree (open, add attributes, add children, close), and the diff harness
 * applies patches by mutating a deep-copied tree in place. It is not thread-safe; the rendering
 * pipeline owns a tree exclusively until it is published as an immutable snapshot for the next diff.
 *
 * <p>{@link #toString()} renders the node as HTML via {@link HtmlBuilder}, primarily for debugging
 * and for equality assertions in tests.
 *
 * @see NodesTreeDiff for the algorithm that consumes pairs of trees
 * @see HtmlBuilder for SSR serialization
 * @see rsp.dsl.Html for the DSL that produces these nodes
 */
public final class TagNode implements Node {

    public final XmlNs xmlns;
    public final String name;
    public final boolean isSelfClosing;

    public final CopyOnWriteArraySet<AttributeNode> attributes = new CopyOnWriteArraySet<>();
    public final List<Node> children = new ArrayList<>();

    /**
     * A stable identity segment for keyed list diffing, or {@code null} when this node is unkeyed.
     * Produced by the {@link rsp.dsl.Html#key} directives: numeric keys yield {@code "kn<decimal>"},
     * string keys yield {@code "ks<escaped>"}. When all children of a parent are keyed, the diff
     * matches them by this value instead of by sibling position.
     */
    private String key;

    public TagNode(final XmlNs xmlns, final String name, boolean isSelfClosing) {
        this.xmlns = Objects.requireNonNull(xmlns);
        this.name = Objects.requireNonNull(name);
        this.isSelfClosing = isSelfClosing;
    }

    public String key() {
        return key;
    }

    public void setKey(final String key) {
        this.key = key;
    }

    public void addChild(final Node node) {
        Objects.requireNonNull(node);
        children.add(node);
    }

    public void addAttribute(final String name, final String value, final boolean isProperty) {
        Objects.requireNonNull(name);
        Objects.requireNonNull(value);
        attributes.add(new AttributeNode(name, value, isProperty));
    }

    @Override
    public String toString() {
        final HtmlBuilder htmlBuilder = new HtmlBuilder(new StringBuilder(), true);
        htmlBuilder.buildHtml(this);
        return htmlBuilder.toString();
    }
}
