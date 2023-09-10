package rsp.page;

import rsp.dom.VirtualDomPath;

public interface RenderContextFactory {
    RenderContext newContext(VirtualDomPath path);
    RenderContext newContext();
}
