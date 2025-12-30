package rsp.examples.crud;

import rsp.compositions.*;
import rsp.examples.crud.components.PostsListContract;
import rsp.examples.crud.components.PostsModule;
import rsp.examples.crud.components.SimpleListView;
import rsp.examples.crud.services.PostService;

public class CrudApp {
    public static void main(String[] args) {
        final PostService postService = new PostService();
        final PostsModule postsModule = new PostsModule(postService);
        
        final Router router = new Router()
                .route("/posts", PostsListContract.class);
        
        final UiRegistry uiRegistry = new UiRegistry()
                .register(PostsListContract.class, contract -> new SimpleListView(contract));
        
        final App app = new App(AppConfig.DEFAULT, uiRegistry, router, postsModule);
        app.start();
    }
}
