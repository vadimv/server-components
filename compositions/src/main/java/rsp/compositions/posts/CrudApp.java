package rsp.compositions.posts;

import rsp.compositions.*;
import rsp.compositions.posts.components.PostsListContract;
import rsp.compositions.posts.components.PostsModule;
import rsp.compositions.posts.services.PostService;
import rsp.compositions.ui.DefaultListView;
import rsp.jetty.WebServer;

public class CrudApp {
    static void main(String[] args) {

        final UiRegistry uiRegistry = new UiRegistry()
                .register(ListView.class,   DefaultListView::new); // Sets a concrete UI implementation

        final Router router = new Router()
                .route("/posts", PostsListContract.class);

        final PostService postService = new PostService();
        final PostsModule postsModule = new PostsModule(postService);
        final App app = new App(new AppConfig(), uiRegistry, router, postsModule);

        final WebServer server = new WebServer(8080, app);
        server.start();
        server.join();
    }
}
