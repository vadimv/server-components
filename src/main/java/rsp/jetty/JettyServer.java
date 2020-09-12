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

import javax.servlet.http.HttpServlet;
import javax.websocket.Endpoint;
import javax.websocket.server.ServerEndpointConfig;

public class JettyServer {

    private static final int MAX_THREADS = 50;

    private final int port;
    private final String basePath;
    private final HttpServlet httpServlet;
    private final Endpoint webSocketEndpoint;

    private Server server;

    public JettyServer(int port, String basePath, HttpServlet httpServlet, Endpoint webSocketEndpoint) {
        this.port = port;
        this.basePath = basePath;
        this.httpServlet = httpServlet;
        this.webSocketEndpoint = webSocketEndpoint;
    }

    public void start() throws Exception {
        final QueuedThreadPool threadPool = new QueuedThreadPool();
        threadPool.setMaxThreads(MAX_THREADS);
        
        server = new Server(threadPool);
        
        final ServerConnector connector = new ServerConnector(server);
        connector.setPort(port);
        server.setConnectors(new Connector[] {connector});

        final ResourceHandler resource_handler = new ResourceHandler();
        resource_handler.setDirectoriesListed(true);
        resource_handler.setResourceBase(".");
        ContextHandler resourceContextHandler = new ContextHandler();
        resourceContextHandler.setHandler(resource_handler);
        resourceContextHandler.setContextPath("/static/*");

        final ServletContextHandler context = new ServletContextHandler();
        context.setContextPath("/" + basePath);
        context.addServlet(new ServletHolder(httpServlet),"/*");

        WebSocketServerContainerInitializer.configure(context, (servletContext, serverContainer) -> {
            final ServerEndpointConfig config =
                    ServerEndpointConfig.Builder.create(webSocketEndpoint.getClass(), "/bridge/web-socket/{pid}/{sid}")
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
