package rsp.page;

import rsp.component.ComponentRenderContext;
import rsp.dom.VirtualDomPath;

public interface RenderContextFactory {
    ComponentRenderContext newContext(VirtualDomPath path);
}
