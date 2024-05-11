package rsp.page;

import rsp.component.ComponentRenderContext;
import rsp.dom.TreePositionPath;

public interface RenderContextFactory {
    ComponentRenderContext newContext(TreePositionPath path);
}
