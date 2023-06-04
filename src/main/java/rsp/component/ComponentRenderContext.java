package rsp.component;

import rsp.page.PageRenderContext;
import rsp.server.OutMessages;

public interface ComponentRenderContext extends PageRenderContext {

    OutMessages out();

}
