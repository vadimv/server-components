package rsp.component;

import rsp.page.LivePage;
import rsp.page.PageRenderContext;

public interface ComponentRenderContext extends PageRenderContext {
    <S> StatefulComponent<S> component();

}
