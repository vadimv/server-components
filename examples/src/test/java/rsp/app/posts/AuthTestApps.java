package rsp.app.posts;

import rsp.app.posts.components.*;
import rsp.app.posts.services.CommentService;
import rsp.app.posts.services.PostService;
import rsp.compositions.application.App;
import rsp.compositions.application.Config;
import rsp.compositions.application.Services;
import rsp.compositions.auth.*;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Group;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.routing.Router;
import rsp.compositions.ui.DefaultEditView;
import rsp.compositions.ui.DefaultListView;
import rsp.jetty.WebServer;
import rsp.server.StaticResources;

import java.io.File;
import java.util.List;

/**
 * Factory methods for creating test app configurations with different auth providers.
 */
class AuthTestApps {

    static Composition postsComposition() {
        final PostService postService = new PostService();
        final CommentService commentService = new CommentService();

        final Router router = new Router()
                .route("/posts", PostsListContract.class)
                .route("/posts/:id", PostEditContract.class)
                .route("/comments", CommentsListContract.class)
                .route("/comments/:id", CommentEditContract.class);

        final Group mainContracts = new Group("Admin")
                .add(new Group("Posts")
                        .bind(PostsListContract.class, ctx -> new PostsListContract(ctx, postService), DefaultListView::new)
                        .bind(PostCreateContract.class, ctx -> new PostCreateContract(ctx, postService), DefaultEditView::new)
                        .bind(PostEditContract.class, ctx -> new PostEditContract(ctx, postService), DefaultEditView::new))
                .add(new Group("Comments")
                        .bind(CommentsListContract.class, ctx -> new CommentsListContract(ctx, commentService), DefaultListView::new)
                        .bind(CommentCreateContract.class, ctx -> new CommentCreateContract(ctx, commentService), DefaultEditView::new)
                        .bind(CommentEditContract.class, ctx -> new CommentEditContract(ctx, commentService), DefaultEditView::new));

        final Group systemContracts = new Group()
                .bind(ExplorerContract.class, ctx -> new ExplorerContract(ctx, mainContracts.structureTree()), ExplorerView::new)
                .bind(HeaderContract.class, HeaderContract::new, HeaderView::new);

        final DefaultLayout layout = new DefaultLayout()
                .leftSidebar(ExplorerContract.class)
                .header(HeaderContract.class);

        return new Composition(router, layout, mainContracts, systemContracts);
    }

    static WebServer simpleAuth(int port) {
        final SimpleAuthProvider authProvider = new SimpleAuthProvider();

        final Router authRouter = new Router().route("/auth/login", LoginContract.class);
        final Group authGroup = new Group()
                .bind(LoginContract.class, LoginContract::new,
                      () -> new SimpleLoginComponent(authProvider));
        final Composition authComposition = new Composition(authRouter, new DefaultLayout(), authGroup);

        final Services services = new Services()
                .service(AuthComponent.AuthProvider.class, authProvider);

        final App app = new App(new Config(), List.of(authComposition, postsComposition()), services);
        final WebServer server = new WebServer(port, app,
                new StaticResources(new File("src/main/java/rsp/app/posts"), "/res/"));
        server.start();
        return server;
    }

    static WebServer basicAuth(int port) {
        final BasicAuthProvider authProvider = new BasicAuthProvider()
                .user("admin", "pass123", "admin");

        final Services services = new Services()
                .service(AuthComponent.AuthProvider.class, authProvider);

        final App app = new App(new Config(), List.of(postsComposition()), services);
        final WebServer server = new WebServer(port, app,
                new StaticResources(new File("src/main/java/rsp/app/posts"), "/res/"));
        server.start();
        return server;
    }

    static WebServer oauthPKCE(int port, int oauthPort) {
        final var oauthConfig = new OAuthPKCEProvider.OAuthConfig(
                "http://localhost:" + oauthPort + "/authorize",
                "http://localhost:" + oauthPort + "/token",
                "http://localhost:" + oauthPort + "/userinfo",
                "test-client",
                null,
                "http://localhost:" + port + "/auth/callback",
                "/auth/login",
                "/auth/signin",
                "/auth/callback",
                "/auth/signout",
                "openid profile email"
        );
        final var authProvider = new OAuthPKCEProvider(oauthConfig);

        final Services services = new Services()
                .service(AuthComponent.AuthProvider.class, authProvider);

        final App app = new App(new Config(), List.of(authProvider.authComposition(), postsComposition()), services);
        final WebServer server = new WebServer(port, app,
                new StaticResources(new File("src/main/java/rsp/app/posts"), "/res/"));
        server.start();
        return server;
    }
}
