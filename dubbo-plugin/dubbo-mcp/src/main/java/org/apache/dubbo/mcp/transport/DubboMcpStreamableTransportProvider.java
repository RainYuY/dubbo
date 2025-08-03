package org.apache.dubbo.mcp.transport;

import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.remoting.http12.HttpMethods;
import org.apache.dubbo.remoting.http12.HttpRequest;
import org.apache.dubbo.remoting.http12.message.ServerSentEvent;
import org.apache.dubbo.rpc.RpcContext;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpStreamableServerSession;
import io.modelcontextprotocol.spec.McpStreamableServerSession.Factory;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;
import reactor.core.publisher.Mono;


/**
 * Implementation of {@link McpStreamableServerTransportProvider} for the Dubbo MCP transport.
 * This class provides methods to manage streamable server sessions and notify clients.
 */
public class DubboMcpStreamableTransportProvider implements McpStreamableServerTransportProvider {

    private Factory sessionFactory;

    private final ObjectMapper objectMapper;

    public static final String SESSION_ID_HEADER = "mcp-session-id";


    public DubboMcpStreamableTransportProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }


    @Override
    public void setSessionFactory(Factory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Mono<Void> notifyClients(String method, Object params) {
        return null;
    }

    @Override
    public void close() {
    }

    @Override
    public Mono<Void> closeGracefully() {
        return null;
    }

    public void handleRequest(StreamObserver<ServerSentEvent<String>> responseObserver) {
        // Handle the request and return the response
        HttpRequest request = RpcContext.getServiceContext().getRequest(HttpRequest.class);
        if (HttpMethods.isGet(request.method())) {
            handleGet(responseObserver);

        } else if (HttpMethods.isPost(request.method())) {
            handlePost(responseObserver);
        }
        return;
    }

    private void handleGet(StreamObserver<ServerSentEvent<String>> responseObserver){
        HttpRequest request = RpcContext.getServiceContext().getRequest(HttpRequest.class);
        // check header

    }

    private void handlePost(StreamObserver<ServerSentEvent<String>> responseObserver){

    }
}
