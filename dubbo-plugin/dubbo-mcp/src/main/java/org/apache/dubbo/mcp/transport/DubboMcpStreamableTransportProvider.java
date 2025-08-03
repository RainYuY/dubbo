package org.apache.dubbo.mcp.transport;

import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.remoting.http12.HttpRequest;
import org.apache.dubbo.remoting.http12.message.ServerSentEvent;
import org.apache.dubbo.rpc.RpcContext;

import io.modelcontextprotocol.spec.McpStreamableServerSession.Factory;
import io.modelcontextprotocol.spec.McpStreamableServerTransportProvider;
import reactor.core.publisher.Mono;


/**
 * Implementation of {@link McpStreamableServerTransportProvider} for the Dubbo MCP transport.
 * This class provides methods to manage streamable server sessions and notify clients.
 */
public class DubboMcpStreamableTransportProvider implements McpStreamableServerTransportProvider {

    @Override
    public void setSessionFactory(Factory sessionFactory) {

    }

    @Override
    public Mono<Void> notifyClients(String method, Object params) {
        return null;
    }

    @Override
    public void close() {
        McpStreamableServerTransportProvider.super.close();
    }

    @Override
    public Mono<Void> closeGracefully() {
        return null;
    }

    public void handleRequest(StreamObserver<ServerSentEvent<String>> responseObserver){
        HttpRequest request = RpcContext.getServiceContext().getRequest(HttpRequest.class);
        request.method();
    }

    private void handleGet(StreamObserver<ServerSentEvent<String>> responseObserver){

    }
}
