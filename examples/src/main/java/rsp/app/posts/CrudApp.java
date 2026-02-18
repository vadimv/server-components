package rsp.app.posts;

import rsp.app.posts.components.CommentCreateContract;
import rsp.app.posts.components.CommentEditContract;
import rsp.app.posts.components.CommentsListContract;
import rsp.app.posts.components.ExplorerContract;
import rsp.app.posts.components.ExplorerView;
import rsp.app.posts.components.PostCreateContract;
import rsp.app.posts.components.PostEditContract;
import rsp.app.posts.components.HeaderContract;
import rsp.app.posts.components.HeaderView;
import rsp.app.posts.components.PromptContract;
import rsp.app.posts.components.PromptView;
import rsp.app.posts.components.PostsListContract;
import rsp.app.posts.services.CommentService;
import rsp.app.posts.services.PostService;
import rsp.app.posts.services.PromptService;
import rsp.compositions.application.App;
import rsp.compositions.application.Config;
import rsp.compositions.application.Services;
import rsp.compositions.auth.AuthComponent;
import rsp.compositions.auth.StubAuthProvider;
import rsp.compositions.composition.Category;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.UiRegistry;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.routing.Router;
import rsp.compositions.ui.DefaultEditView;
import rsp.compositions.ui.DefaultListView;
import rsp.jetty.WebServer;
import rsp.server.StaticResources;

import java.io.File;
import java.util.List;

public class CrudApp {
    static void main(final String[] args) {
        run(true);
    }

    public static WebServer run(final boolean blockCurrentThread) {
        final Config config = new Config()
                .with(System.getProperties());

        // Router defines URL routes for this composition
        final Router router = new Router()
                .route("/posts", PostsListContract.class)
                .route("/posts/:id", PostEditContract.class)
                .route("/comments", CommentsListContract.class)
                .route("/comments/:id", CommentEditContract.class);

        final UiRegistry postsUi = new UiRegistry()
                .register(PostsListContract.class, PostsListContract::new, DefaultListView::new)
                .register(PostCreateContract.class, PostCreateContract::new, DefaultEditView::new)
                .register(PostEditContract.class, PostEditContract::new, DefaultEditView::new)

                .register(CommentsListContract.class, CommentsListContract::new, DefaultListView::new)
                .register(CommentCreateContract.class, CommentCreateContract::new, DefaultEditView::new)
                .register(CommentEditContract.class, CommentEditContract::new, DefaultEditView::new)

                .register(ExplorerContract.class, ExplorerContract::new, ExplorerView::new)
                .register(PromptContract.class, PromptContract::new, PromptView::new)
                .register(HeaderContract.class, HeaderContract::new, HeaderView::new);

        final Category categories = new Category()
                .group(new Category("Posts"), PostsListContract.class, PostCreateContract.class, PostEditContract.class)
                .group(new Category("Comments"), CommentsListContract.class, CommentCreateContract.class, CommentEditContract.class);

        final DefaultLayout layout = new DefaultLayout()
                .leftSidebar(ExplorerContract.class)
                .rightSidebar(PromptContract.class)
                .header(HeaderContract.class);



        final Composition postsComposition = new Composition(router, postsUi, categories, layout);

        // Create services
        final PostService postService = new PostService();
        final CommentService commentService = new CommentService();
        final PromptService promptService = new PromptService();
        promptService.startTicking();

        // Services and auth provider will be added to the components context and referenced by their classes
        final Services services = new Services()
                .service(PostService.class, postService)
                .service(CommentService.class, commentService)
                .service(PromptService.class, promptService)
                .service(AuthComponent.AuthProvider.class, new StubAuthProvider());

        final App app = new App(config, List.of(postsComposition), services);

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
