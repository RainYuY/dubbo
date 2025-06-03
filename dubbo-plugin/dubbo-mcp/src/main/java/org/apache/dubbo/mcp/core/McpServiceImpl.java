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
package org.apache.dubbo.mcp.core;

import org.apache.dubbo.common.resource.Disposable;
import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.mcp.transport.DubboMcpSseTransportProvider;
import org.apache.dubbo.mcp.transport.DubboMcpStreamableTransportProvider;
import org.apache.dubbo.remoting.http12.message.ServerSentEvent;

public class McpServiceImpl implements McpService, Disposable {

    private DubboMcpSseTransportProvider sseTransportProvider = getSseTransportProvider();

    private DubboMcpStreamableTransportProvider streamableTransportProvider = getStreamableTransportProvider();

    @Override
    public void get(StreamObserver<ServerSentEvent<String>> responseObserver) {
        if (sseTransportProvider == null) {
            sseTransportProvider = getSseTransportProvider();
        }
        sseTransportProvider.handleRequest(responseObserver);
    }

    @Override
    public void post() {
        if (sseTransportProvider == null) {
            sseTransportProvider = getSseTransportProvider();
        }
        sseTransportProvider.handleRequest(null);
    }

    @Override
    public void streamable(StreamObserver<ServerSentEvent<String>> responseObserver) {
        if (streamableTransportProvider == null) {
            streamableTransportProvider = getStreamableTransportProvider();
        }
        streamableTransportProvider.handleRequest(responseObserver);
    }

    private DubboMcpSseTransportProvider getSseTransportProvider() {
        return McpApplicationDeployListener.getDubboMcpSseTransportProvider();
    }

    private DubboMcpStreamableTransportProvider getStreamableTransportProvider() {
        return McpApplicationDeployListener.getDubboMcpStreamableTransportProvider();
    }

    @Override
    public void destroy() {}
}
