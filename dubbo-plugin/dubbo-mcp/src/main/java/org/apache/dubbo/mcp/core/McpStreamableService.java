package org.apache.dubbo.mcp.core;

import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.remoting.http12.HttpMethods;
import org.apache.dubbo.remoting.http12.message.ServerSentEvent;
import org.apache.dubbo.remoting.http12.rest.Mapping;

import static org.apache.dubbo.mcp.McpConstant.SETTINGS_MCP_PATHS_MESSAGE;

@Mapping("")
public interface McpStreamableService {

    @Mapping(value = "//${" + SETTINGS_MCP_PATHS_MESSAGE + ":/mcp/streamable/message}")
    void streamable(StreamObserver<ServerSentEvent<String>> responseObserver);
}
