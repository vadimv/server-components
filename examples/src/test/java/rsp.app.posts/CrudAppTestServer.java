package rsp.app.posts;

import rsp.app.posts.components.CommentCreateContract;
import rsp.app.posts.components.CommentEditContract;
import rsp.app.posts.components.CommentsListContract;
import rsp.app.posts.components.ExplorerContract;
import rsp.app.posts.components.ExplorerView;
import rsp.app.posts.components.PostCreateContract;
import rsp.app.posts.components.PostEditContract;
import rsp.app.posts.components.PromptContract;
import rsp.app.posts.components.PromptView;
import rsp.app.posts.components.PostsListContract;
import rsp.app.posts.services.CommentService;
import rsp.app.posts.services.PostService;
import rsp.app.posts.services.PromptService;
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

/**
 * Test server wrapper for CrudApp to enable integration testing with Playwright.
 * Similar to Counters.java pattern, provides non-blocking startup for test lifecycle management.
 */
public final class CrudAppTestServer {
    public static final int PORT = 8080;

    public final WebServer webServer;

    public CrudAppTestServer(final WebServer webServer) {
        this.webServer = webServer;
    }

    public static CrudAppTestServer run(final boolean blockCurrentThread) {
        final AppConfig appConfig = AppConfig.fromSystemProperties();

        final UiRegistry uiRegistry = new UiRegistry()
                .register(ListViewContract.class, DefaultListView::new)
                .register(CreateViewContract.class, DefaultEditView::new)
                .register(EditViewContract.class, DefaultEditView::new)
                .register(ExplorerContract.class, ExplorerView::new)
                .register(PromptContract.class, PromptView::new);

        final Router router = new Router()
                .route("/posts", PostsListContract.class)
                .route("/posts/:id", PostEditContract.class)
                .route("/comments", CommentsListContract.class)
                .route("/comments/:id", CommentEditContract.class);

        final ViewsPlacements places = new ViewsPlacements()
                .place(Slot.LEFT_SIDEBAR, ExplorerContract.class, ExplorerContract::new)
                .place(Slot.PRIMARY, PostsListContract.class, PostsListContract::new)
                .place(Slot.PRIMARY, CommentsListContract.class, CommentsListContract::new)
                .place(Slot.OVERLAY, PostCreateContract.class, PostCreateContract::new)
                .place(Slot.OVERLAY, PostEditContract.class, PostEditContract::new)
                .place(Slot.OVERLAY, CommentCreateContract.class, CommentCreateContract::new)
                .place(Slot.OVERLAY, CommentEditContract.class, CommentEditContract::new)
                .place(Slot.RIGHT_SIDEBAR, PromptContract.class, PromptContract::new);

        final Composition postsModule = new Composition(router, places);

        final PostService postService = new PostService();
        final CommentService commentService = new CommentService();
        final PromptService promptService = new PromptService();
        promptService.startTicking();

        final var services = List.of(postService,
                                     commentService,
                                     promptService,
                                     new StubAuthProvider());

        final App app = new App(appConfig, uiRegistry, List.of(postsModule), services);

        final CrudAppTestServer server = new CrudAppTestServer(
            new WebServer(PORT,
                         app,
                         new StaticResources(new File("src/main/java/rsp/app/posts"),
                                           "/res/"))
        );

        server.webServer.start();

        if (blockCurrentThread) {
            server.webServer.join();
        }

        return server;
    }

    public void stop() throws Exception {
        webServer.stop();
    }

    public static void main(final String[] args) {
        run(true);
    }
}
