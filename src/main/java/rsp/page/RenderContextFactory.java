package rsp.page;

import rsp.component.ComponentRenderContext;
import rsp.dom.TreePositionPath;

/**
 * Provides an abstraction for creating an instance of ComponentRenderContext class.
 */
public interface RenderContextFactory {
    ComponentRenderContext newContext(TreePositionPath path);
}
