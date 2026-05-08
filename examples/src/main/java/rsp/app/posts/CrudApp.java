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
import rsp.app.posts.services.OllamaAgentService;
import rsp.app.posts.services.PostService;
import rsp.app.posts.services.PromptService;
import rsp.app.posts.services.RegexAgentService;
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
import rsp.compositions.contract.FormViewContract;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.layout.GroupPlacementPolicy;
import rsp.compositions.layout.Placement;
import rsp.compositions.routing.Router;
import rsp.compositions.ui.DefaultEditView;
import rsp.compositions.ui.DefaultListView;
import rsp.jetty.WebServer;
import rsp.server.StaticResources;

import java.io.File;
import java.time.Duration;
import java.util.List;
import java.util.Locale;

/**
 * Starts the Posts and Comments demo app.
 *
 * <p>This class keeps the app wiring in one place so the example shows how routes, pages, layout,
 * login, and the prompt sidebar fit together.
 */
public class CrudApp {
    private final AgentService agentService;

    public CrudApp() {
        this(new AgentService());
    }

    public CrudApp(AgentService agentService) {
        this.agentService = agentService;
    }

    public static void main(String[] args) {
        new CrudApp(resolveAgentService()).run(true);
    }

    /**
     * Assembles the application and starts the web server. The body is structured as a sequence of small stages so
     * the wiring can be read top-to-bottom: routes, services, agent permissions, contract groups,
     * layout, then the login composition.
     *
     * @param blockCurrentThread when {@code true} the call blocks on {@code server.join()} so the
     *                           {@code main} method does not exit; tests pass {@code false}.
     */
    public WebServer run(final boolean blockCurrentThread) {
        final Config config = new Config()
                .with(System.getProperties());

        // URL to contract mapping. Literal segments ("/posts/new") must precede parameter
        // routes ("/posts/:id") or "/posts/new" would be treated as id "new".
        final Router router = new Router()
                .route("/posts", PostsListContract.class)
                .route("/", PostsListContract.class)
                .route("/posts/new", PostCreateContract.class)
                .route("/posts/:id", PostEditContract.class)
                .route("/comments", CommentsListContract.class)
                .route("/comments/new", CommentCreateContract.class)
                .route("/comments/:id", CommentEditContract.class);

        // Application services. They are passed into contract constructors below so contracts
        // remain free of static singletons and easy to swap in tests.
        final PostService postService = new PostService();
        final CommentService commentService = new CommentService();
        final PromptService promptService = new PromptService();
        promptService.startTicking();

        // Agent permissions. The policy says which agent actions are allowed. When an action needs
        // user consent, the prompt asks this spawner for an agent session. Approval decisions are
        // remembered for the browser session.
        final ActionDispatcher actionDispatcher = new ActionDispatcher();
        final AccessPolicy policy = new CompositePolicy(ExamplePolicies.requireGrantForExecution(),
                                                        ExamplePolicies.grantConstraints());
        final Authorization authorization = new Authorization(policy, Attributes.empty());
        final DelegationStore delegationStore = new InMemoryDelegationStore();
        final AgentSpawner spawner = new ApprovalSpawner(new PolicySpawner(authorization), delegationStore);

        // A contract is the logic behind a UI fragment: it owns state, actions, and the data schema.
        // A view renders that contract.
        // The nested group names become the sidebar menu.
        final Group mainContracts = new Group("Admin").description("Administration panel")
                .add(new Group("Posts").description("Blog posts with create, edit, delete, and search")
                        .bind(PostsListContract.class, ctx -> new PostsListContract(ctx, postService), DefaultListView::new)
                        .bind(PostCreateContract.class, ctx -> new PostCreateContract(ctx, postService), DefaultEditView::new)
                        .bind(PostEditContract.class, ctx -> new PostEditContract(ctx, postService), DefaultEditView::new))
                .add(new Group("Comments").description("User comments for the posts")
                        .bind(CommentsListContract.class, ctx -> new CommentsListContract(ctx, commentService), DefaultListView::new)
                        .bind(CommentCreateContract.class, ctx -> new CommentCreateContract(ctx, commentService), DefaultEditView::new)
                        .bind(CommentEditContract.class, ctx -> new CommentEditContract(ctx, commentService), DefaultEditView::new));

        // These views support the page but are not menu items. Explorer builds the sidebar from
        // mainContracts; Prompt lets the user talk to the agent; Header shows the session;
        // DelegationApproval appears only when consent is needed.
        final Group systemContracts = new Group()
                .bind(ExplorerContract.class, ctx -> new ExplorerContract(ctx, mainContracts.structureTree()), ExplorerView::new)
                .bind(PromptContract.class, ctx -> new PromptContract(ctx, promptService, agentService, actionDispatcher, authorization, spawner, mainContracts.structureTree()), PromptView::new)
                .bind(HeaderContract.class, HeaderContract::new, HeaderView::new)
                .bind(DelegationApprovalContract.class, ctx -> new DelegationApprovalContract(ctx, delegationStore), DelegationApprovalView::new);

        // Layout chooses where each contract appears. The sidebars and header are always visible.
        // Forms replace the main content; approval is always a modal.
        final DefaultLayout layout = new DefaultLayout()
                .leftSidebar(ExplorerContract.class)
                .rightSidebar(PromptContract.class)
                .header(HeaderContract.class)
                .groupPlacementPolicy(GroupPlacementPolicy.FIRST_IN_GROUP_INLINE_OTHERS_MODAL)
                .placement(FormViewContract.class, Placement.INLINE.primary())
                .placement(DelegationApprovalContract.class, Placement.MODAL);

        // This is the posts feature package: routes decide which page is active, layout decides
        // where it appears, and both user-facing and support contract groups are available to the scene.
        final Composition postsComposition = new Composition(router, layout, mainContracts, systemContracts);

        // Login lives in its own composition. The auth provider redirects anonymous users to
        // /auth/login, which keeps login code out of the posts composition.
        final SimpleAuthProvider authProvider = new SimpleAuthProvider();
        final Router authRouter = new Router()
                .route("/auth/login", LoginContract.class);
        final Group authGroup = new Group()
                .bind(LoginContract.class, LoginContract::new, () -> new SimpleLoginComponent(authProvider));
        final Composition authComposition = new Composition(authRouter, new DefaultLayout(), authGroup);

        // App-wide services available to any contract. The auth provider is stored here so
        // AuthComponent can find it on every request.
        final Services services = new Services()
                .service(AuthComponent.AuthProvider.class, authProvider);

        // Compositions are tried in order; the login route is checked before the posts routes.
        final App app = new App(config, List.of(authComposition, postsComposition), services);

        final WebServer server = new WebServer(8085,
                                               app,
                                               new StaticResources(resolvePostsResourceDir(),
                                                                   "/res/"));
        server.start();
        if (blockCurrentThread) {
            server.join();
        }
        return server;
    }

    /**
     * Locates the directory served at {@code /res/} (CSS and other static assets). The example may
     * be launched either from the repository root or from the {@code examples} module, so both
     * candidate paths are tried.
     */
    private static File resolvePostsResourceDir() {
        for (String candidate : List.of("src/main/java/rsp/app/posts",
                                        "examples/src/main/java/rsp/app/posts")) {
            File dir = new File(candidate);
            if (dir.isDirectory()) {
                return dir;
            }
        }
        throw new IllegalStateException("Could not locate posts static resources.");
    }

    /**
     * Selects the AI backend used by the prompt sidebar based on the {@code -Dai.agent} system
     * property. {@code regex} is a deterministic, dependency-free default useful for tests and
     * demos; {@code claude} and {@code ollama} call out to real LLMs and require their respective
     * environment configuration.
     */
    private static AgentService resolveAgentService() {
        String backend = System.getProperty("ai.agent", "regex").toLowerCase(Locale.ROOT);
        return switch (backend) {
            case "regex" -> new RegexAgentService();
            case "claude" -> new ClaudeAgentService(
                    System.getenv("ANTHROPIC_API_KEY"),
                    System.getProperty("rsp.agent.model", "claude-haiku-4-5-20251001"),
                    Duration.ofSeconds(Long.getLong("rsp.agent.timeoutSeconds", 30L))
            );
            case "ollama" -> new OllamaAgentService(
                    System.getProperty("rsp.agent.url", "http://127.0.0.1:11434/api/chat"),
                    System.getProperty("rsp.agent.model", "mistral"),
                    Duration.ofSeconds(Long.getLong("rsp.agent.timeoutSeconds", 120L))
            );
            default -> throw new IllegalArgumentException("Unknown rsp.agent backend: " + backend);
        };
    }
}
