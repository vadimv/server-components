package rsp.app.posts;

import rsp.app.posts.components.*;
import rsp.app.posts.services.*;
import rsp.application.ApplicationConfig;
import rsp.application.ApplicationContext;
import rsp.compositions.agent.*;
import rsp.compositions.agentui.DelegationApprovalBlock;
import rsp.compositions.agentui.PromptBlock;
import rsp.compositions.agentui.PromptService;
import rsp.compositions.application.App;
import rsp.compositions.auth.LoginBlock;
import rsp.http.auth.SimpleAuthProvider;
import rsp.compositions.authorization.*;
import rsp.compositions.composition.Composition;
import rsp.compositions.composition.Group;
import rsp.compositions.block.FormBlock;
import rsp.compositions.dashboard.DashboardBlock;
import rsp.compositions.layout.DefaultLayout;
import rsp.compositions.layout.GroupPlacementPolicy;
import rsp.compositions.layout.Placement;
import rsp.compositions.block.BlockTarget;
import rsp.compositions.routing.BlockRoutes;
import rsp.compositions.shell.ExplorerBlock;
import rsp.url.routing.RouteTable;
import rsp.compositions.shell.HeaderBlock;
import rsp.compositions.ui.DefaultFormView;
import rsp.compositions.ui.DefaultListView;
import rsp.http.WebServer;
import rsp.http.Pages;
import rsp.http.StaticResources;

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
     * the wiring can be read top-to-bottom: routes, services, agent permissions, block groups,
     * layout, then the login composition.
     *
     * @param blockCurrentThread when {@code true} the call blocks on {@code server.join()} so the
     *                           {@code main} method does not exit; tests pass {@code false}.
     */
    public WebServer run(final boolean blockCurrentThread) {
        final ApplicationConfig config = new ApplicationConfig()
                .with(System.getProperties());

        // URL to block mapping. Literal segments ("/posts/new") must precede parameter
        // routes ("/posts/{id}") or "/posts/new" would be treated as id "new".
        final Object postsKey = new Object();
        final RouteTable<BlockTarget> routes = BlockRoutes.builder()
                .route("/dashboard", DashboardBlock.class)
                .route("/posts", postsKey, PostsListBlock.class)
                .route("/", postsKey, PostsListBlock.class)
                .route("/posts/new", PostCreateBlock.class)
                .route("/posts/{id}", PostEditBlock.class)
                .route("/comments", CommentsListBlock.class)
                .route("/comments/new", CommentCreateBlock.class)
                .route("/comments/{id}", CommentEditBlock.class)
                .build();

        // Application services. They are passed into block constructors below so blocks
        // remain free of static singletons and easy to swap in tests.
        final PostService postService = new PostService();
        final CommentService commentService = new CommentService(postService::exists);
        postService.onDelete(commentService::deleteByPostId);
        final PromptService promptService = new PromptService();
        final CommentRateStreamService commentRateStreamService = new CommentRateStreamService();
        final LogStreamService logStreamService = new LogStreamService();
        final SimpleAuthProvider authProvider = new SimpleAuthProvider();
        final var dashboardDefinition = DemoDashboards.definition();
        final var dashboardRuntime = DemoDashboards.runtime(
                DemoTelemetry.registry(commentRateStreamService, logStreamService));

        // Agent permissions. The policy says which agent actions are allowed. When an action needs
        // user consent, the prompt asks this spawner for an agent session. Approval decisions are
        // remembered for the browser session.
        final ActionDispatcher actionDispatcher = new ActionDispatcher();
        final AccessPolicy policy = new CompositePolicy(ExamplePolicies.requireGrantForExecution(),
                                                        ExamplePolicies.grantConstraints());
        final Authorization authorization = new Authorization(policy, Attributes.empty());
        final DelegationStore delegationStore = new InMemoryDelegationStore();
        final AgentSpawner spawner = new ApprovalSpawner(new PolicySpawner(authorization), delegationStore);

        // A block is the logic behind a UI fragment: it owns state, actions, and the data schema.
        // A view renders that block.
        // The nested group names become the sidebar menu.
        final Group mainBlocks = new Group("Admin").description("Administration panel")
                .add(new Group("Dashboard").description("Live dashboard widgets for the admin overview")
                        .bind(DashboardBlock.class,
                                () -> new DashboardBlock(dashboardDefinition, dashboardRuntime)))
                .add(new Group("Posts").description("Blog posts with create, edit, delete, and search")
                        .bind(postsKey, PostsListBlock.class,
                                () -> new PostsListBlock(postService, new DefaultListView()))
                        .bind(PostCreateBlock.class, () -> new PostCreateBlock(postService, new DefaultFormView()))
                        .bind(PostEditBlock.class, () -> new PostEditBlock(postService, new DefaultFormView())))
                .add(new Group("Comments").description("User comments for the posts")
                        .bind(CommentsListBlock.class, () -> new CommentsListBlock(commentService, new DefaultListView()))
                        .bind(CommentCreateBlock.class, () -> new CommentCreateBlock(commentService, postService, new DefaultFormView()))
                        .bind(CommentEditBlock.class, () -> new CommentEditBlock(commentService, postService, new DefaultFormView())));

        // These views support the page but are not menu items. Explorer builds the sidebar from
        // mainBlocks; Prompt lets the user talk to the agent; Header shows the session;
        // DelegationApproval appears only when consent is needed.
        final Group systemBlocks = new Group()
                .bind(ExplorerBlock.class, () -> new ExplorerBlock(mainBlocks.structureTree()))
                .bind(PromptBlock.class, () -> new PromptBlock(promptService, agentService, actionDispatcher, authorization, spawner, mainBlocks.structureTree()))
                .bind(HeaderBlock.class, () -> new HeaderBlock(authProvider.signOutPath()))
                .bind(DelegationApprovalBlock.class, () -> new DelegationApprovalBlock(delegationStore));

        // Layout chooses where each block appears. The sidebars and header are always visible.
        // Forms replace the main content; approval is always a modal.
        final DefaultLayout layout = new DefaultLayout()
                .leftSidebar(ExplorerBlock.class)
                .rightSidebar(PromptBlock.class)
                .header(HeaderBlock.class)
                .groupPlacementPolicy(GroupPlacementPolicy.FIRST_IN_GROUP_INLINE_OTHERS_MODAL)
                .placement(FormBlock.class, Placement.INLINE.primary())
                .placement(DelegationApprovalBlock.class, Placement.MODAL);

        // This is the posts feature package: routes decide which page is active, layout decides
        // where it appears, and both user-facing and support block groups are available to the scene.
        final Composition postsComposition = new Composition(routes, layout, mainBlocks, systemBlocks);

        // Login lives in its own composition. The auth provider redirects anonymous users to
        // /auth/login, which keeps login code out of the posts composition.
        final RouteTable<BlockTarget> authRoutes = BlockRoutes.builder()
                .route("/auth/login", LoginBlock.class)
                .build();
        final Group authGroup = new Group()
                .bind(LoginBlock.class, () -> new LoginBlock(authProvider.signInPath(), true));
        final Composition authComposition = new Composition(authRoutes, new DefaultLayout(), authGroup);

        // App-wide process services available to any block. Request authentication is
        // deliberately absent: it is resolved by the HTTP adapter before page creation.
        final ApplicationContext applicationContext = ApplicationContext.builder()
                .config(config)
                .service(PromptService.class, promptService)
                .service(CommentRateStreamService.class, commentRateStreamService)
                .service(LogStreamService.class, logStreamService)
                .build();

        // Compositions are tried in order; the login route is checked before the posts routes.
        final App app = new App(applicationContext, List.of(authComposition, postsComposition));

        final WebServer server = WebServer.pages(8085,
                                                 authProvider.pages(app,
                                                         (request, authentication) -> Pages.live(
                                                                 app.apply(request.relativeUrl(), authentication))),
                                                 new StaticResources(resolvePostsResourceDir(), "/res/"));
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
