package rsp.jetty;
import org.eclipse.jetty.server.Connector;
import org.eclipse.jetty.server.Handler;
import org.eclipse.jetty.server.Server;
import org.eclipse.jetty.server.ServerConnector;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerList;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.util.thread.QueuedThreadPool;
import org.eclipse.jetty.websocket.jsr356.server.deploy.WebSocketServerContainerInitializer;
import rsp.App;
import rsp.javax.web.MainHttpServlet;
import rsp.javax.web.MainWebSocketEndpoint;
import javax.websocket.server.ServerEndpointConfig;

public class JettyServer {

    private static final int MAX_THREADS = 50;

    private final App app;

    private Server server;

    public JettyServer(App app) {
        this.app = app;
    }

    public void start() throws Exception {
        final QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setMaxThreads(MAX_THREADS);
        
        server = new Server(threadPool);
        
        final ServerConnector connector = new ServerConnector(server);
        connector.setPort(app.port);
        server.setConnectors(new Connector[] {connector});

        final ResourceHandler resourcesHandler = new ResourceHandler();
        resourcesHandler.setDirectoriesListed(true);
        resourcesHandler.setResourceBase("./target");
        ContextHandler resourceContextHandler = new ContextHandler();
        resourceContextHandler.setHandler(resourcesHandler);
        resourceContextHandler.setContextPath("/static/*");

        final ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/" + app.basePath);
        context.addServlet(new ServletHolder(new MainHttpServlet<>(app.pageRendering())),"/*");

        final MainWebSocketEndpoint webSocketEndpoint =  new MainWebSocketEndpoint<>(app.pagesStorage);
        WebSocketServerContainerInitializer.configure(context, (servletContext, serverContainer) -> {
            final ServerEndpointConfig config =
                    ServerEndpointConfig.Builder.create(webSocketEndpoint.getClass(), app.WS_ENDPOINT_PATH)
                                                .configurator(new ServerEndpointConfig.Configurator() {
                                                    public <T> T getEndpointInstance(Class<T> clazz) throws InstantiationException {
                                                        return (T)webSocketEndpoint;
                                                    }
                                                }).build();
            serverContainer.addEndpoint(config);
        });

        final HandlerList handlers = new HandlerList();
        handlers.setHandlers(new Handler[] { resourceContextHandler, context });
        server.setHandler(handlers);
        server.start();    
    }
    
    public void join() throws InterruptedException {
        server.join();
    }
}
