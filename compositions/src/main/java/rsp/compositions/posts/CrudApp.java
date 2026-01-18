package rsp.compositions.posts;

import rsp.compositions.*;
import rsp.compositions.auth.StubAuthProvider;
import rsp.compositions.posts.components.PostEditContract;
import rsp.compositions.posts.components.PostsListContract;
import rsp.compositions.posts.components.PostsModule;
import rsp.compositions.posts.services.PostService;
import rsp.compositions.ui.DefaultEditView;
import rsp.compositions.ui.DefaultListView;
import rsp.jetty.WebServer;
import rsp.server.StaticResources;

import java.io.File;
import java.util.List;

public class CrudApp {
    public static void main(final String[] args) {

        final UiRegistry uiRegistry = new UiRegistry()
                .register(ListViewContract.class, DefaultListView::new) // Sets a concrete UI implementation
                .register(EditViewContract.class, DefaultEditView::new); // Register EditView UI

        // Sets a frame for this application's address bar path patterns
        // Route order matters: specific routes before parameterized ones
        final Router router = new Router()
                .route("/posts", PostsListContract.class)
                .route("/posts/new", PostEditContract.class)  // Create route (must come before :id)
                .route("/posts/:id", PostEditContract.class); // Edit route

        // Application configuration (non-sensitive, flows to all contracts/components)
        // Loads from system properties (e.g., -Dapp.pageSize.default=20)
        final AppConfig appConfig = AppConfig.fromSystemProperties();

        // Create services
        // NOTE: When migrating to real database service, you would:
        // 1. Create ServiceConfig with DB credentials (used ONLY for service init, NEVER in context)
        // 2. Pass ServiceConfig to service constructor: new PostService(serviceConfig.getDatabaseConfig())
        // 3. Only the service INSTANCE goes to context, not ServiceConfig
        final PostService postService = new PostService();

        // Services and auth provider will be added to the components context and referenced by their classes
        final var services =  List.of(postService,
                                      new StubAuthProvider());// Optional: defaults to anonymous if omitted
        // Create modules (no longer need service references)
        final PostsModule postsModule = new PostsModule();

        // Create app with AppConfig (flows to AppComponent → Context)
        final App app = new App(appConfig, uiRegistry, router, List.of(postsModule), services);

        final WebServer server = new WebServer(8080,
                                               app,
                                               new StaticResources(new File("src/main/java/rsp/compositions/posts"),
                                                                   "/res/"));
        server.start();
        server.join();
    }
}
