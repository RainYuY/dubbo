/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.dubbo.mcp.transport;

import org.apache.dubbo.cache.support.expiring.ExpiringMap;
import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.JsonUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.http12.HttpMethods;
import org.apache.dubbo.remoting.http12.HttpRequest;
import org.apache.dubbo.remoting.http12.HttpResult;
import org.apache.dubbo.remoting.http12.HttpStatus;
import org.apache.dubbo.remoting.http12.HttpUtils;
import org.apache.dubbo.remoting.http12.message.MediaType;
import org.apache.dubbo.remoting.http12.message.ServerSentEvent;
import org.apache.dubbo.rpc.RpcContext;

import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpError;
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

    private final ExpiringMap<String, McpStreamableServerSession> sessions = new ExpiringMap<>(30 * 60, 30);

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
    public void close() {}

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

    private void handleGet(StreamObserver<ServerSentEvent<String>> responseObserver) {
        HttpRequest request = RpcContext.getServiceContext().getRequest(HttpRequest.class);
        // check header
        List<String> accepts = HttpUtils.parseAccept(request.accept());
        if (CollectionUtils.isEmpty(accepts)
                || !accepts.contains(MediaType.TEXT_EVENT_STREAM.getName())
                || !accepts.contains(MediaType.APPLICATION_JSON.getName())) {
            // 如果没有包含必须类型，则返回异常
            responseObserver.onError(HttpResult.builder()
                    .header("Content-Type", MediaType.APPLICATION_JSON.getName())
                    .status(HttpStatus.NOT_ACCEPTABLE.getCode())
                    .body(JsonUtils.toJson(new McpError("Unsupported accept type").getJsonRpcError()))
                    .build()
                    .toPayload());
            responseObserver.onCompleted();
            return;
        }

        String sessionId =
                RpcContext.getServiceContext().getRequest(HttpRequest.class).header(SESSION_ID_HEADER);
        if (StringUtils.isBlank(sessionId)) {
            // 如果没有sessionId，则返回异常
            responseObserver.onError(HttpResult.builder()
                    .header("Content-Type", MediaType.APPLICATION_JSON.getName())
                    .status(HttpStatus.BAD_REQUEST.getCode())
                    .body(JsonUtils.toJson(new McpError("Session ID missing in request header").getJsonRpcError()))
                    .build()
                    .toPayload());
            responseObserver.onCompleted();
            return;
        }
        McpStreamableServerSession session = this.sessions.get(sessionId);

        if (session == null) {
            responseObserver.onError(HttpResult.builder()
                    .header("Content-Type", MediaType.APPLICATION_JSON.getName())
                    .status(HttpStatus.NOT_FOUND.getCode())
                    .build()
                    .toPayload());
            responseObserver.onCompleted();
            return;
        }
    }

    private void handlePost(StreamObserver<ServerSentEvent<String>> responseObserver) {}
}
