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
import rsp.app.posts.services.ClaudeAgentService;
import rsp.app.posts.services.PostService;
import rsp.app.posts.services.PromptService;
import rsp.compositions.agent.AgentService;
import rsp.compositions.agent.AgentSpawner;
import rsp.compositions.agent.ApprovalSpawner;
import rsp.compositions.agent.DelegationApprovalContract;
import rsp.compositions.agent.DelegationApprovalView;
import rsp.compositions.agent.DelegationStore;
import rsp.compositions.agent.InMemoryDelegationStore;
import rsp.compositions.agent.ActionDispatcher;
import rsp.compositions.agent.PolicySpawner;
import rsp.compositions.authorization.AccessPolicy;
import rsp.compositions.authorization.Attributes;
import rsp.compositions.authorization.CompositePolicy;
import rsp.compositions.authorization.ExamplePolicies;
import rsp.compositions.authorization.Authorization;
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

public class CrudApp {
    private final AgentService agentService;

    public CrudApp() {
        this(new AgentService());
    }

    public CrudApp(AgentService agentService) {
        this.agentService = agentService;
    }

    static void main(final String[] args) {
   //     new CrudApp().run(true);

/*        var agent = new OllamaAgentService(
                "http://127.0.0.1:11434/api/chat",
                "mistral",   // was "tinyllama"
                java.time.Duration.ofSeconds(120)
        );
        new CrudApp(agent).run(true);*/

        var agent = new ClaudeAgentService(
                System.getenv("ANTHROPIC_API_KEY"),
                "claude-haiku-4-5-20251001",
                java.time.Duration.ofSeconds(30)
        );
        new CrudApp(agent).run(true);


    }

    public WebServer run(final boolean blockCurrentThread) {
        final Config config = new Config()
                .with(System.getProperties());

        // Router defines URL routes for this composition
        final Router router = new Router()
                .route("/posts", PostsListContract.class)
                .route("/", PostsListContract.class)
                .route("/posts/:id", PostEditContract.class)
                .route("/comments", CommentsListContract.class)
                .route("/comments/:id", CommentEditContract.class);

        final PostService postService = new PostService();
        final CommentService commentService = new CommentService();
        final PromptService promptService = new PromptService();
        promptService.startTicking();

        // Agent services — unified ABAC authorization for spawn, discovery, and execution
        final ActionDispatcher actionDispatcher = new ActionDispatcher();
        final AccessPolicy policy = new CompositePolicy(ExamplePolicies.grantConstraints());
        final Authorization authorization = new Authorization(policy, Attributes.empty());
        final DelegationStore delegationStore = new InMemoryDelegationStore();
        final AgentSpawner spawner = new ApprovalSpawner(new PolicySpawner(authorization), delegationStore);

        final Group mainContracts = new Group("Admin").description("Administration panel")
                .add(new Group("Posts").description("Blog posts with create, edit, delete, and search")
                        .bind(PostsListContract.class, ctx -> new PostsListContract(ctx, postService), DefaultListView::new)
                        .bind(PostCreateContract.class, ctx -> new PostCreateContract(ctx, postService), DefaultEditView::new)
                        .bind(PostEditContract.class, ctx -> new PostEditContract(ctx, postService), DefaultEditView::new))
                .add(new Group("Comments").description("User comments for the posts")
                        .bind(CommentsListContract.class, ctx -> new CommentsListContract(ctx, commentService), DefaultListView::new)
                        .bind(CommentCreateContract.class, ctx -> new CommentCreateContract(ctx, commentService), DefaultEditView::new)
                        .bind(CommentEditContract.class, ctx -> new CommentEditContract(ctx, commentService), DefaultEditView::new));

        final Group systemContracts = new Group()
                .bind(ExplorerContract.class, ctx -> new ExplorerContract(ctx, mainContracts.structureTree()), ExplorerView::new)
                .bind(PromptContract.class, ctx -> new PromptContract(ctx, promptService, agentService, actionDispatcher, authorization, spawner, mainContracts.structureTree()), PromptView::new)
                .bind(HeaderContract.class, HeaderContract::new, HeaderView::new)
                .bind(DelegationApprovalContract.class, ctx -> new DelegationApprovalContract(ctx, delegationStore), DelegationApprovalView::new);

        final DefaultLayout layout = new DefaultLayout()
                .leftSidebar(ExplorerContract.class)
                .rightSidebar(PromptContract.class)
                .header(HeaderContract.class);

        final Composition postsComposition = new Composition(router, layout, mainContracts, systemContracts);

        // Auth provider with in-memory session store
        final SimpleAuthProvider authProvider = new SimpleAuthProvider();
        // Auth composition: login page at /auth/login
        final Router authRouter = new Router()
                .route("/auth/login", LoginContract.class);
        final Group authGroup = new Group()
                .bind(LoginContract.class, LoginContract::new, () -> new SimpleLoginComponent(authProvider));
        final Composition authComposition = new Composition(authRouter, new DefaultLayout(), authGroup);
        final Services services = new Services()
                .service(AuthComponent.AuthProvider.class, authProvider);

        // Auth composition first — login page route matched before posts routes
        final App app = new App(config, List.of(authComposition, postsComposition), services);

        final WebServer server = new WebServer(8085,
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
