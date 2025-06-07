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

import org.apache.dubbo.common.stream.StreamObserver;
import org.apache.dubbo.mcp.transport.DubboMcpSseTransportProvider;
import org.apache.dubbo.remoting.http12.message.ServerSentEvent;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.MockitoAnnotations;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class McpSseServiceTest {

    @Mock
    private DubboMcpSseTransportProvider transportProvider;

    @Mock
    private StreamObserver<ServerSentEvent<String>> responseObserver;

    private McpSseServiceImpl mcpSseServiceImpl;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        mcpSseServiceImpl = new McpSseServiceImpl();
    }

    @Test
    void testGet_WithValidObserver() {
        try (MockedStatic<McpApplicationDeployListener> mockedStatic = mockStatic(McpApplicationDeployListener.class)) {

            mockedStatic
                    .when(McpApplicationDeployListener::getDubboMcpSseTransportProvider)
                    .thenReturn(transportProvider);

            mcpSseServiceImpl.get(responseObserver);

            verify(transportProvider).handleRequest(responseObserver);
        }
    }

    @Test
    void testGet_WithNullTransportProvider() {
        try (MockedStatic<McpApplicationDeployListener> mockedStatic = mockStatic(McpApplicationDeployListener.class)) {

            mockedStatic
                    .when(McpApplicationDeployListener::getDubboMcpSseTransportProvider)
                    .thenReturn(null);

            assertThrows(NullPointerException.class, () -> mcpSseServiceImpl.get(responseObserver));
        }
    }

    @Test
    void testPost() {
        try (MockedStatic<McpApplicationDeployListener> mockedStatic = mockStatic(McpApplicationDeployListener.class)) {

            mockedStatic
                    .when(McpApplicationDeployListener::getDubboMcpSseTransportProvider)
                    .thenReturn(transportProvider);

            mcpSseServiceImpl.post();

            verify(transportProvider).handleRequest(null);
        }
    }

    @Test
    void testPost_WithNullTransportProvider() {
        try (MockedStatic<McpApplicationDeployListener> mockedStatic = mockStatic(McpApplicationDeployListener.class)) {

            mockedStatic
                    .when(McpApplicationDeployListener::getDubboMcpSseTransportProvider)
                    .thenReturn(null);

            assertThrows(NullPointerException.class, () -> mcpSseServiceImpl.post());
        }
    }

    @Test
    void testServiceInterface() {

        McpSseService service = mcpSseServiceImpl;
        assertNotNull(service);

        assertTrue(service instanceof McpSseService);
    }

    @Test
    void testServiceImplementation() {

        assertNotNull(mcpSseServiceImpl);
        assertTrue(mcpSseServiceImpl instanceof McpSseService);
    }

    @Test
    void testDestroy() {

        assertDoesNotThrow(() -> mcpSseServiceImpl.destroy());
    }
}
