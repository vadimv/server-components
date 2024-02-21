package rsp.html;

/**
 * The base class for all DLS definitions classes.
 * Contains an implementation of the {@link Object#toString()} method.
 */
public abstract class BaseSegmentDefinition implements SegmentDefinition {

/*    @Override
    public String toString() {
        final RenderContext renderContext = new ComponentRenderContext(VirtualDomPath.DOCUMENT,
                                                                     new AtomicReference<>());
        render(renderContext);
        return renderContext.toString();
    }*/
}
