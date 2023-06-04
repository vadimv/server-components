package rsp.component;

import rsp.page.PageRenderContext;
import rsp.server.Out;

public interface ComponentRenderContext extends PageRenderContext {
    Out out();
}
