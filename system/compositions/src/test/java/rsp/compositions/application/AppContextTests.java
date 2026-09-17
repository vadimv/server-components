package rsp.compositions.application;

import org.junit.jupiter.api.Test;
import rsp.application.ApplicationConfig;
import rsp.application.ApplicationContext;
import rsp.authentication.Authentication;
import rsp.component.ComponentContext;
import rsp.component.ContextKey;
import rsp.compositions.block.ContextKeys;
import rsp.url.Fragment;
import rsp.url.Path;
import rsp.url.Query;
import rsp.url.RelativeUrl;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

class AppContextTests {
    @Test
    void projects_application_context_without_owning_its_lifecycle() {
        Object service = new Object();
        ApplicationConfig config = new ApplicationConfig().with("page.size", "25");
        ApplicationContext applicationContext = ApplicationContext.builder()
                .config(config)
                .service(Object.class, service)
                .build();
        App app = new App(applicationContext, List.of());
        AppComponent component = (AppComponent) app.apply(
                new RelativeUrl(Path.of("/"), Query.EMPTY, Fragment.EMPTY),
                Authentication.anonymous());

        ComponentContext projected = component.subComponentsContext().apply(
                new ComponentContext(), new AppComponent.AppComponentState());

        assertSame(applicationContext, projected.get(ApplicationContext.class));
        assertSame(config, projected.get(ApplicationConfig.class));
        assertSame(service, projected.get(Object.class));
        assertEquals("25", projected.get(new ContextKey.StringKey<>("page.size", String.class)));
        assertSame(Authentication.anonymous(), projected.get(Authentication.class));
        assertEquals(ApplicationContext.State.NEW, applicationContext.state());
    }
}
