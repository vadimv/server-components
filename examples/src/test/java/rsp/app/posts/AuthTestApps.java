package rsp.app.posts;

import rsp.app.posts.components.*;
import rsp.compositions.shell.ExplorerBlock;
import rsp.compositions.shell.HeaderBlock;
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
import rsp.compositions.ui.DefaultFormView;
import rsp.compositions.ui.DefaultListView;
import rsp.http.WebServer;
import rsp.server.StaticResources;

import java.io.File;
import java.util.List;

/**
 * Factory methods for creating test app configurations with different auth providers.
 */
class AuthTestApps {

    static Composition postsComposition() {
        final PostService postService = new PostService();
        final CommentService commentService = new CommentService(postService::exists);
        postService.onDelete(commentService::deleteByPostId);

        final Router router = new Router()
                .route("/posts", PostsListBlock.class)
                .route("/posts/:id", PostEditBlock.class)
                .route("/comments", CommentsListBlock.class)
                .route("/comments/:id", CommentEditBlock.class);

        final Group mainBlocks = new Group("Admin")
                .add(new Group("Posts")
                        .bind(PostsListBlock.class, () -> new PostsListBlock(postService, new DefaultListView()))
                        .bind(PostCreateBlock.class, () -> new PostCreateBlock(postService, new DefaultFormView()))
                        .bind(PostEditBlock.class, () -> new PostEditBlock(postService, new DefaultFormView())))
                .add(new Group("Comments")
                        .bind(CommentsListBlock.class, () -> new CommentsListBlock(commentService, new DefaultListView()))
                        .bind(CommentCreateBlock.class, () -> new CommentCreateBlock(commentService, new DefaultFormView()))
                        .bind(CommentEditBlock.class, () -> new CommentEditBlock(commentService, new DefaultFormView())));

        final Group systemBlocks = new Group()
                .bind(ExplorerBlock.class, () -> new ExplorerBlock(mainBlocks.structureTree()))
                .bind(HeaderBlock.class, HeaderBlock::new);

        final DefaultLayout layout = new DefaultLayout()
                .leftSidebar(ExplorerBlock.class)
                .header(HeaderBlock.class);

        return new Composition(router, layout, mainBlocks, systemBlocks);
    }

    static WebServer simpleAuth(int port) {
        final SimpleAuthProvider authProvider = new SimpleAuthProvider();

        final Router authRouter = new Router().route("/auth/login", LoginBlock.class);
        final Group authGroup = new Group()
                .bind(LoginBlock.class, () -> new LoginBlock(authProvider));
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
