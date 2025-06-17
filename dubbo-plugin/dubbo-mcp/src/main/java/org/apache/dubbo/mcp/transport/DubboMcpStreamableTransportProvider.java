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
import org.apache.dubbo.common.utils.IOUtils;
import org.apache.dubbo.common.utils.JsonUtils;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.remoting.http12.HttpHeaderNames;
import org.apache.dubbo.remoting.http12.HttpHeaders;
import org.apache.dubbo.remoting.http12.HttpMethods;
import org.apache.dubbo.remoting.http12.HttpRequest;
import org.apache.dubbo.remoting.http12.HttpResponse;
import org.apache.dubbo.remoting.http12.HttpResult;
import org.apache.dubbo.remoting.http12.HttpStatus;
import org.apache.dubbo.remoting.http12.HttpUtils;
import org.apache.dubbo.remoting.http12.ServerHttpChannelObserver;
import org.apache.dubbo.remoting.http12.message.MediaType;
import org.apache.dubbo.remoting.http12.message.ServerSentEvent;
import org.apache.dubbo.rpc.RpcContext;

import java.nio.charset.StandardCharsets;
import java.util.List;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpError;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerSession;
import io.modelcontextprotocol.spec.McpServerTransportProvider;
import reactor.core.publisher.Mono;

public class DubboMcpStreamableTransportProvider implements McpServerTransportProvider {

    private McpServerSession.Factory sessionFactory;

    private final ObjectMapper objectMapper;

    public static final String SESSION_ID_HEADER = "mcp-session-id";

    private final ExpiringMap<String, McpServerSession> sessions = new ExpiringMap<>(30 * 60, 30);

    public DubboMcpStreamableTransportProvider(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
        sessions.getExpireThread().startExpiryIfNotStarted();
    }

    @Override
    public void setSessionFactory(McpServerSession.Factory sessionFactory) {
        this.sessionFactory = sessionFactory;
    }

    @Override
    public Mono<Void> notifyClients(String method, Object params) {
        return null;
    }

    @Override
    public void close() {
        McpServerTransportProvider.super.close();
    }

    @Override
    public Mono<Void> closeGracefully() {
        return null;
    }

    public void handleRequest(StreamObserver<ServerSentEvent<String>> responseObserver) {
        // Handle the request and return the response
        HttpRequest request = RpcContext.getServiceContext().getRequest(HttpRequest.class);
        if (HttpMethods.isGet(request.method())) {

        } else if (HttpMethods.isPost(request.method())) {
            handlePostRequest(responseObserver);
        }
        return;
    }

    public void handleGetRequest(StreamObserver<ServerSentEvent<Object>> responseObserver) {
        String sessionId = RpcContext.getServiceContext().getRequest(HttpRequest.class).header(SESSION_ID_HEADER);
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
        if (!sessions.containsKey(sessionId)) {
            // 如果没有找到对应的session，则返回异常
            responseObserver.onError(HttpResult.builder()
                    .header("Content-Type", MediaType.APPLICATION_JSON.getName())
                    .status(HttpStatus.NOT_FOUND.getCode())
                    .body(JsonUtils.toJson(new McpError("Session not found").getJsonRpcError()))
                    .build()
                    .toPayload());
            responseObserver.onCompleted();
            return;
        }
        // TODO:implement GET request handling logic
        responseObserver.onNext(null);
        return;
    }

    public void handlePostRequest(StreamObserver<ServerSentEvent<String>> responseObserver) {
        // streamable 模式下,首先需要确认客户端支持的accept类型
        HttpRequest request = RpcContext.getServiceContext().getRequest(HttpRequest.class);
        HttpResponse response = RpcContext.getServiceContext().getResponse(HttpResponse.class);
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
        McpServerSession mcpServerSession = null;
        try {
            McpSchema.JSONRPCMessage message = McpSchema.deserializeJsonRpcMessage(
                    objectMapper, IOUtils.read(request.inputStream(), String.valueOf(StandardCharsets.UTF_8)));
            // 分情况讨论
            // 如果是初始化的场景，则为其创建一个新的session
            if (message instanceof McpSchema.JSONRPCRequest
                    && McpSchema.METHOD_INITIALIZE.equals(((McpSchema.JSONRPCRequest) message).method())) {
                DubboMcpSessionTransport dubboMcpSessionTransport =
                        new DubboMcpSessionTransport(responseObserver, objectMapper);
                mcpServerSession = sessionFactory.create(dubboMcpSessionTransport);
                sessions.put(mcpServerSession.getId(), mcpServerSession);
                if (responseObserver instanceof ServerHttpChannelObserver){
                    McpServerSession finalMcpServerSession = mcpServerSession;
                    ((ServerHttpChannelObserver) responseObserver).addHeadersCustomizer((hs, t) -> ((HttpHeaders)hs).add(
                            SESSION_ID_HEADER, finalMcpServerSession.getId()
                    ));
                }
            } else {
                // 首先检查Header是否有sessionId
                String sessionId = request.header(SESSION_ID_HEADER);
                if (StringUtils.isBlank(sessionId)) {
                    // 如果没有sessionId，则返回异常
                    response.setStatus(HttpStatus.BAD_REQUEST.getCode());
                    response.setBody(JsonUtils.toJson(new McpError("Session ID missing in request header")));
                    responseObserver.onCompleted();
                    return;
                }
                // 如果有sessionId，则从sessions中获取对应的session
                mcpServerSession = sessions.get(sessionId);
                if (mcpServerSession == null) {
                    // 如果没有找到对应的session，则返回异常
                    response.setStatus(HttpStatus.NOT_FOUND.getCode());
                    response.setBody(JsonUtils.toJson(new McpError("Session not found")));
                    responseObserver.onCompleted();
                    return;
                }
                refreshSessionExpire(mcpServerSession);
            }
            mcpServerSession.handle(message).block();
            responseObserver.onCompleted();
        } catch (Exception e) {
            // 如果反序列化失败，则返回异常
            responseObserver.onError(HttpResult.builder()
                    .header("Content-Type", MediaType.APPLICATION_JSON.getName())
                    .status(HttpStatus.INTERNAL_SERVER_ERROR.getCode())
                    .body(JsonUtils.toJson(new McpError("Invalid message format").getJsonRpcError()))
                    .build()
                    .toPayload());
            responseObserver.onCompleted();
            return;
        }
    }

    public void handleDeleteRequest(StreamObserver<ServerSentEvent<String>> responseObserver) {
        return;
    }

    private void refreshSessionExpire(McpServerSession session) {
        sessions.put(session.getId(), session);
    }
}
