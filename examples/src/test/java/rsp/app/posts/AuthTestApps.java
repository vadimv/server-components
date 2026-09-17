package rsp.app.posts;

import rsp.app.posts.components.*;
import rsp.application.ApplicationContext;
import rsp.compositions.shell.ExplorerBlock;
import rsp.compositions.shell.HeaderBlock;
import rsp.app.posts.services.CommentService;
import rsp.app.posts.services.PostService;
import rsp.compositions.application.App;
import rsp.compositions.auth.*;
import rsp.http.auth.BasicAuthProvider;
import rsp.http.auth.OAuthPKCEProvider;
import rsp.http.auth.SimpleAuthProvider;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Group;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.routing.BlockRoutes;
import rsp.compositions.ui.DefaultFormView;
import rsp.compositions.ui.DefaultListView;
import rsp.http.WebServer;
import rsp.http.Pages;
import rsp.http.StaticResources;

import java.io.File;
import java.util.List;

/**
 * Factory methods for creating test app configurations with different auth providers.
 */
class AuthTestApps {

    static Composition postsComposition(String signOutPath) {
        final PostService postService = new PostService();
        final CommentService commentService = new CommentService(postService::exists);
        postService.onDelete(commentService::deleteByPostId);

        final BlockRoutes.Builder routes = BlockRoutes.builder()
                .route("/posts", PostsListBlock.class)
                .route("/posts/{id}", PostEditBlock.class)
                .route("/comments", CommentsListBlock.class)
                .route("/comments/{id}", CommentEditBlock.class);

        final Group mainBlocks = new Group("Admin")
                .add(new Group("Posts")
                        .bind(PostsListBlock.class, () -> new PostsListBlock(postService, new DefaultListView()))
                        .bind(PostCreateBlock.class, () -> new PostCreateBlock(postService, new DefaultFormView()))
                        .bind(PostEditBlock.class, () -> new PostEditBlock(postService, new DefaultFormView())))
                .add(new Group("Comments")
                        .bind(CommentsListBlock.class, () -> new CommentsListBlock(commentService, new DefaultListView()))
                        .bind(CommentCreateBlock.class, () -> new CommentCreateBlock(commentService, postService, new DefaultFormView()))
                        .bind(CommentEditBlock.class, () -> new CommentEditBlock(commentService, postService, new DefaultFormView())));

        final Group systemBlocks = new Group()
                .bind(ExplorerBlock.class, () -> new ExplorerBlock(mainBlocks.structureTree()))
                .bind(HeaderBlock.class, () -> new HeaderBlock(signOutPath));

        final DefaultLayout layout = new DefaultLayout()
                .leftSidebar(ExplorerBlock.class)
                .header(HeaderBlock.class);

        return new Composition(routes, layout, mainBlocks, systemBlocks);
    }

    static WebServer simpleAuth(int port) {
        final SimpleAuthProvider authProvider = new SimpleAuthProvider();

        final BlockRoutes.Builder authRoutes = BlockRoutes.builder().route("/auth/login", LoginBlock.class);
        final Group authGroup = new Group()
                .bind(LoginBlock.class, () -> new LoginBlock(authProvider.signInPath(), true));
        final Composition authComposition = new Composition(authRoutes, new DefaultLayout(), authGroup);

        final App app = new App(context(), List.of(authComposition, postsComposition(authProvider.signOutPath())));
        final WebServer server = WebServer.pages(port, authProvider.pages(app,
                        (request, authentication) -> Pages.live(app.apply(request.relativeUrl(), authentication))),
                new StaticResources(new File("src/main/java/rsp/app/posts"), "/res/"));
        server.start();
        return server;
    }

    static WebServer basicAuth(int port) {
        final BasicAuthProvider authProvider = new BasicAuthProvider()
                .user("admin", "pass123", "admin");

        final App app = new App(context(), List.of(postsComposition(null)));
        final WebServer server = WebServer.pages(port, authProvider.pages(app,
                        (request, authentication) -> Pages.live(app.apply(request.relativeUrl(), authentication))),
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

        final BlockRoutes.Builder authRoutes = BlockRoutes.builder()
                .route(authProvider.loginPath(), LoginBlock.class);
        final Group authGroup = new Group()
                .bind(LoginBlock.class, () -> new LoginBlock(authProvider.signInPath()));
        final Composition authComposition = new Composition(authRoutes, new DefaultLayout(), authGroup);

        final App app = new App(context(),
                List.of(authComposition, postsComposition(authProvider.signOutPath())));
        final WebServer server = WebServer.pages(port, authProvider.pages(app,
                        (request, authentication) -> Pages.live(app.apply(request.relativeUrl(), authentication))),
                new StaticResources(new File("src/main/java/rsp/app/posts"), "/res/"));
        server.start();
        return server;
    }

    private static ApplicationContext context() {
        return ApplicationContext.builder().build();
    }
}
