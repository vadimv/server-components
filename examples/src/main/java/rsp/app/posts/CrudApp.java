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
import rsp.compositions.auth.AuthComponent;
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
import java.util.Map;

public class CrudApp {
    static void main(final String[] args) {
        run(true);
    }

    public static WebServer run(final boolean blockCurrentThread) {
        final AppConfig appConfig = AppConfig.fromSystemProperties();

        final UiRegistry uiRegistry = new UiRegistry()
                .register(ListViewContract.class, DefaultListView::new)
                .register(CreateViewContract.class, DefaultEditView::new)
                .register(EditViewContract.class, DefaultEditView::new)
                .register(ExplorerContract.class, ExplorerView::new)
                .register(PromptContract.class, PromptView::new);

        // Router defines URL routes for this composition
        // OVERLAY contracts (create/edit) are typically triggered by events, not URLs
        // However, edit contracts have routes to enable direct URL editing
        final Router router = new Router()
                .route("/posts", PostsListContract.class)
                .route("/posts/:id", PostEditContract.class)
                .route("/comments", CommentsListContract.class)
                .route("/comments/:id", CommentEditContract.class);

        final ViewsPlacements places = new ViewsPlacements()
                .place(Slot.LEFT_SIDEBAR, ExplorerContract.class, ExplorerContract::new)  // Explorer in sidebar
                .place(Slot.PRIMARY, PostsListContract.class, PostsListContract::new)
                .place(Slot.PRIMARY, CommentsListContract.class, CommentsListContract::new)
                .place(Slot.OVERLAY, PostCreateContract.class, PostCreateContract::new)
                .place(Slot.OVERLAY, PostEditContract.class, PostEditContract::new)
                .place(Slot.OVERLAY, CommentCreateContract.class, CommentCreateContract::new)
                .place(Slot.OVERLAY, CommentEditContract.class, CommentEditContract::new)
                .place(Slot.RIGHT_SIDEBAR, PromptContract.class, PromptContract::new);

        final Composition postsModule = new Composition(router, places);

        // Create services
        final PostService postService = new PostService();
        final CommentService commentService = new CommentService();
        final PromptService promptService = new PromptService();
        promptService.startTicking();

        // Services and auth provider will be added to the components context and referenced by their classes
        final var services = Map.of(PostService.class, postService,
                                    CommentService.class, commentService,
                                    PromptService.class,  promptService,
                                    AuthComponent.AuthProvider.class, new StubAuthProvider());

        // Create app with AppConfig
        final App app = new App(appConfig, uiRegistry, List.of(postsModule), services);

        final WebServer server = new WebServer(8080,
                                               app,
                                               new StaticResources(new File("src/main/java/rsp/app/posts"),
                                                                   "/res/"));
        server.start();
        if (blockCurrentThread) {
            server.join();
        }
        return server;
    }
}
