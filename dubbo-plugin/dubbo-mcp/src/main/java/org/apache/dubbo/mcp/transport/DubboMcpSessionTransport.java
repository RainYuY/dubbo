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

import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.remoting.http12.message.ServerSentEvent;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpServerTransport;
import reactor.core.publisher.Mono;

import static org.apache.dubbo.mcp.transport.DubboMcpSseTransportProvider.MESSAGE_EVENT_TYPE;

public class DubboMcpSessionTransport implements McpServerTransport {

    private final ObjectMapper JSON;

    private final StreamObserver<ServerSentEvent<String>> responseObserver;

    public DubboMcpSessionTransport(
            StreamObserver<ServerSentEvent<String>> responseObserver, ObjectMapper objectMapper) {
        this.responseObserver = responseObserver;
        this.JSON = objectMapper;
    }

    @Override
    public void close() {
        responseObserver.onCompleted();
    }

    @Override
    public Mono<Void> closeGracefully() {
        return Mono.fromRunnable(responseObserver::onCompleted);
    }

    @Override
    public Mono<Void> sendMessage(McpSchema.JSONRPCMessage message) {
        return Mono.fromRunnable(() -> {
            try {
                String jsonText = JSON.writeValueAsString(message);
                responseObserver.onNext(ServerSentEvent.<String>builder()
                        .event(MESSAGE_EVENT_TYPE)
                        .data(jsonText)
                        .build());
            } catch (Exception e) {
                responseObserver.onError(e);
            }
        });
    }

    @Override
    public <T> T unmarshalFrom(Object data, TypeReference<T> typeRef) {
        return JSON.convertValue(data, typeRef);
    }
}
