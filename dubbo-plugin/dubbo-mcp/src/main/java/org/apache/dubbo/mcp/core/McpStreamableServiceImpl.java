package org.apache.dubbo.mcp.core;

import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.mcp.transport.DubboMcpSseTransportProvider;
import org.apache.dubbo.mcp.transport.DubboMcpStreamableTransportProvider;
import org.apache.dubbo.remoting.http12.message.ServerSentEvent;

public class McpStreamableServiceImpl implements McpStreamableService {

    private DubboMcpStreamableTransportProvider transportProvider = getTransportProvider();


    @Override
    public void streamable(StreamObserver<ServerSentEvent<String>> responseObserver) {

    }

    public DubboMcpStreamableTransportProvider getTransportProvider() {
        return McpApplicationDeployListener.getDubboMcpStreamableTransportProvider();

    }
}
