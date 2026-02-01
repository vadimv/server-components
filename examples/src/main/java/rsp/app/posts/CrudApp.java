package rsp.app.posts;

import rsp.app.posts.components.ExplorerContract;
import rsp.app.posts.components.ExplorerView;
import rsp.app.posts.components.PostCreateContract;
import rsp.app.posts.components.PostEditContract;
import rsp.app.posts.components.PostsListContract;
import rsp.app.posts.services.PostService;
import rsp.compositions.application.App;
import rsp.compositions.application.AppConfig;
import rsp.compositions.auth.StubAuthProvider;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Slot;
import rsp.compositions.composition.UiRegistry;
import rsp.compositions.composition.ViewsPlacements;
import rsp.compositions.contract.CreateViewContract;
import rsp.compositions.contract.EditViewContract;
import rsp.compositions.contract.ListViewContract;
import rsp.compositions.routing.Router;
import rsp.compositions.ui.DefaultEditView;
import rsp.compositions.ui.DefaultListView;
import rsp.jetty.WebServer;
import rsp.server.StaticResources;

import java.io.File;
import java.util.List;

public class CrudApp {
    static void main(final String[] args) {

        final AppConfig appConfig = AppConfig.fromSystemProperties();

        final UiRegistry uiRegistry = new UiRegistry()
                .register(ListViewContract.class, DefaultListView::new)
                .register(CreateViewContract.class, DefaultEditView::new)
                .register(EditViewContract.class, DefaultEditView::new)
                .register(ExplorerContract.class, ExplorerView::new);  // Explorer UI

        // Router defines URL routes for this composition
        // OVERLAY contracts (create/edit) are typically triggered by events, not URLs
        // However, PostEditContract has a route to enable direct URL editing
        final Router router = new Router()
                .route("/posts", PostsListContract.class)
                .route("/posts/:id", PostEditContract.class); // enable editing a post with its direct URL

        final ViewsPlacements places = new ViewsPlacements()
                .place(Slot.LEFT_SIDEBAR, ExplorerContract.class, ExplorerContract::new)  // Explorer in sidebar
                .place(Slot.PRIMARY, PostsListContract.class, PostsListContract::new)
                .place(Slot.OVERLAY, PostCreateContract.class, PostCreateContract::new)
                .place(Slot.OVERLAY, PostEditContract.class, PostEditContract::new);

        final Composition postsModule = new Composition(router, places);

        // Create services
        final PostService postService = new PostService();

        // Services and auth provider will be added to the components context and referenced by their classes
        final var services = List.of(postService,
                                     new StubAuthProvider());// Optional: defaults to anonymous if omitted

        // Create app with AppConfig
        final App app = new App(appConfig, uiRegistry, List.of(postsModule), services);

        final WebServer server = new WebServer(8080,
                                               app,
                                               new StaticResources(new File("src/main/java/rsp/app/posts"),
                                                                   "/res/"));
        server.start();
        server.join();
    }
}
