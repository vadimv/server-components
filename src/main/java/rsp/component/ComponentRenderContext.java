package rsp.component;

import rsp.page.LivePage;
import rsp.page.LivePagePropertiesSnapshot;
import rsp.page.PageRenderContext;
import rsp.server.Out;

import java.util.function.Consumer;

public interface ComponentRenderContext extends PageRenderContext {
    LivePage livePage();

}
