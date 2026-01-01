package rsp.compositions.posts;

import rsp.compositions.*;
import rsp.compositions.posts.components.PostsListContract;
import rsp.compositions.posts.components.PostsModule;
import rsp.compositions.posts.services.PostService;
import rsp.compositions.ui.DefaultListView;
import rsp.jetty.WebServer;

import java.util.List;
import java.util.Map;

public class CrudApp {
    static void main(String[] args) {

        final UiRegistry uiRegistry = new UiRegistry()
                .register(ListViewContract.class, DefaultListView::new); // Sets a concrete UI implementation

        // Sets a frame for this application's address bar path patterns
        final Router router = new Router()
                .route("/posts", PostsListContract.class);

        // Create services
        final PostService postService = new PostService();

        // Register services in a map with namespace keys
        final Map<String, Object> services = Map.of(
                "service.posts", postService
        );

        // Create modules (no longer need service references)
        final PostsModule postsModule = new PostsModule();

        // Create app with services
        final App app = new App(new AppConfig(), uiRegistry, router, List.of(postsModule), services);

        final WebServer server = new WebServer(8080, app);
        server.start();
        server.join();
    }
}
