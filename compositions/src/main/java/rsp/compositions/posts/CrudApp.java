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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CrudApp {
    public static void main(String[] args) {

        final UiRegistry uiRegistry = new UiRegistry()
                .register(ListViewContract.class, DefaultListView::new) // Sets a concrete UI implementation
                .register(EditViewContract.class, DefaultEditView::new); // Register EditView UI

        // Sets a frame for this application's address bar path patterns
        final Router router = new Router()
                .route("/posts", PostsListContract.class)
                .route("/posts/:id", PostEditContract.class);

        // Create services
        final PostService postService = new PostService();

        // Register services and auth provider in a map with namespace keys
        final Map<String, Object> services = new HashMap<>();
        services.put("service.posts", postService);
        services.put("auth.provider", new StubAuthProvider()); // Optional: defaults to anonymous if omitted

        // Create modules (no longer need service references)
        final PostsModule postsModule = new PostsModule();

        // Create app with services
        final App app = new App(new AppConfig(), uiRegistry, router, List.of(postsModule), services);

        final WebServer server = new WebServer(8080, app);
        server.start();
        server.join();
    }
}
