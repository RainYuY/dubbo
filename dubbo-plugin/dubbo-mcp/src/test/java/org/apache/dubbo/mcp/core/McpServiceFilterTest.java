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

import org.apache.dubbo.common.config.Configuration;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.mcp.McpConstant;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ProviderModel;
import org.apache.dubbo.rpc.model.ServiceDescriptor;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class McpServiceFilterTest {

    @Mock
    private ApplicationModel applicationModel;

    @Mock
    private Configuration configuration;

    @Mock
    private ProviderModel providerModel;

    @Mock
    private ServiceDescriptor serviceDescriptor;

    private McpServiceFilter mcpServiceFilter;

    @BeforeEach
    void setUp() {
        reset(configuration, providerModel, serviceDescriptor);
    }

    @Test
    void testShouldExposeAsMcpTool_DefaultDisabled() {
        try (MockedStatic<org.apache.dubbo.common.config.ConfigurationUtils> configUtils =
                mockStatic(org.apache.dubbo.common.config.ConfigurationUtils.class)) {

            configUtils
                    .when(() -> org.apache.dubbo.common.config.ConfigurationUtils.getGlobalConfiguration(any()))
                    .thenReturn(configuration);

            when(configuration.getBoolean(McpConstant.SETTINGS_MCP_DEFAULT_ENABLED, true))
                    .thenReturn(false);
            when(configuration.getString(McpConstant.SETTINGS_MCP_INCLUDE_PATTERNS, ""))
                    .thenReturn("");
            when(configuration.getString(McpConstant.SETTINGS_MCP_EXCLUDE_PATTERNS, ""))
                    .thenReturn("");

            mcpServiceFilter = new McpServiceFilter(applicationModel);

            when(providerModel.getServiceModel()).thenReturn(serviceDescriptor);
            when(serviceDescriptor.getInterfaceName()).thenReturn("com.example.TestService");
            when(providerModel.getServiceInstance()).thenReturn(null);

            boolean result = mcpServiceFilter.shouldExposeAsMcpTool(providerModel);
            assertFalse(result);
        }
    }

    @Test
    void testShouldExposeAsMcpTool_EnabledByAnnotation() {
        try (MockedStatic<org.apache.dubbo.common.config.ConfigurationUtils> configUtils =
                mockStatic(org.apache.dubbo.common.config.ConfigurationUtils.class)) {

            configUtils
                    .when(() -> org.apache.dubbo.common.config.ConfigurationUtils.getGlobalConfiguration(any()))
                    .thenReturn(configuration);

            when(configuration.getBoolean(McpConstant.SETTINGS_MCP_DEFAULT_ENABLED, true))
                    .thenReturn(false);
            when(configuration.getString(McpConstant.SETTINGS_MCP_INCLUDE_PATTERNS, ""))
                    .thenReturn("");
            when(configuration.getString(McpConstant.SETTINGS_MCP_EXCLUDE_PATTERNS, ""))
                    .thenReturn("");

            mcpServiceFilter = new McpServiceFilter(applicationModel);

            @DubboService(mcpEnabled = true)
            class TestService {}

            TestService serviceInstance = new TestService();

            when(providerModel.getServiceModel()).thenReturn(serviceDescriptor);
            when(serviceDescriptor.getInterfaceName()).thenReturn("com.example.TestService");
            when(providerModel.getServiceInstance()).thenReturn(serviceInstance);

            boolean result = mcpServiceFilter.shouldExposeAsMcpTool(providerModel);
            assertTrue(result);
        }
    }

    @Test
    void testShouldExposeAsMcpTool_EnabledByConfig() {
        try (MockedStatic<org.apache.dubbo.common.config.ConfigurationUtils> configUtils =
                mockStatic(org.apache.dubbo.common.config.ConfigurationUtils.class)) {

            configUtils
                    .when(() -> org.apache.dubbo.common.config.ConfigurationUtils.getGlobalConfiguration(any()))
                    .thenReturn(configuration);

            when(configuration.getBoolean(McpConstant.SETTINGS_MCP_DEFAULT_ENABLED, true))
                    .thenReturn(false);
            when(configuration.getString(McpConstant.SETTINGS_MCP_INCLUDE_PATTERNS, ""))
                    .thenReturn("");
            when(configuration.getString(McpConstant.SETTINGS_MCP_EXCLUDE_PATTERNS, ""))
                    .thenReturn("");

            mcpServiceFilter = new McpServiceFilter(applicationModel);

            when(providerModel.getServiceModel()).thenReturn(serviceDescriptor);
            when(serviceDescriptor.getInterfaceName()).thenReturn("com.example.TestService");
            when(providerModel.getServiceInstance()).thenReturn(null);

            String configKey = McpConstant.SETTINGS_MCP_SERVICE_PREFIX + ".com.example.TestService."
                    + McpConstant.SETTINGS_MCP_SERVICE_ENABLED_SUFFIX;
            when(configuration.getBoolean(configKey, (Boolean) null)).thenReturn(true);

            boolean result = mcpServiceFilter.shouldExposeAsMcpTool(providerModel);
            assertTrue(result);
        }
    }

    @Test
    void testShouldExposeAsMcpTool_ExcludedByPattern() {
        try (MockedStatic<org.apache.dubbo.common.config.ConfigurationUtils> configUtils =
                mockStatic(org.apache.dubbo.common.config.ConfigurationUtils.class)) {

            configUtils
                    .when(() -> org.apache.dubbo.common.config.ConfigurationUtils.getGlobalConfiguration(any()))
                    .thenReturn(configuration);

            when(configuration.getBoolean(McpConstant.SETTINGS_MCP_DEFAULT_ENABLED, true))
                    .thenReturn(true);
            when(configuration.getString(McpConstant.SETTINGS_MCP_INCLUDE_PATTERNS, ""))
                    .thenReturn("");
            when(configuration.getString(McpConstant.SETTINGS_MCP_EXCLUDE_PATTERNS, ""))
                    .thenReturn("*.internal.*");

            mcpServiceFilter = new McpServiceFilter(applicationModel);

            when(providerModel.getServiceModel()).thenReturn(serviceDescriptor);
            when(serviceDescriptor.getInterfaceName()).thenReturn("com.example.internal.TestService");
            when(providerModel.getServiceInstance()).thenReturn(null);

            boolean result = mcpServiceFilter.shouldExposeAsMcpTool(providerModel);
            assertFalse(result);
        }
    }

    @Test
    void testShouldExposeAsMcpTool_IncludedByPattern() {
        try (MockedStatic<org.apache.dubbo.common.config.ConfigurationUtils> configUtils =
                mockStatic(org.apache.dubbo.common.config.ConfigurationUtils.class)) {

            configUtils
                    .when(() -> org.apache.dubbo.common.config.ConfigurationUtils.getGlobalConfiguration(any()))
                    .thenReturn(configuration);

            when(configuration.getBoolean(McpConstant.SETTINGS_MCP_DEFAULT_ENABLED, true))
                    .thenReturn(false);
            when(configuration.getString(McpConstant.SETTINGS_MCP_INCLUDE_PATTERNS, ""))
                    .thenReturn("com.example.*");
            when(configuration.getString(McpConstant.SETTINGS_MCP_EXCLUDE_PATTERNS, ""))
                    .thenReturn("");

            mcpServiceFilter = new McpServiceFilter(applicationModel);

            when(providerModel.getServiceModel()).thenReturn(serviceDescriptor);
            when(serviceDescriptor.getInterfaceName()).thenReturn("com.example.TestService");
            when(providerModel.getServiceInstance()).thenReturn(null);

            String serviceSpecificKey = McpConstant.SETTINGS_MCP_SERVICE_PREFIX + ".com.example.TestService."
                    + McpConstant.SETTINGS_MCP_SERVICE_ENABLED_SUFFIX;
            when(configuration.getBoolean(serviceSpecificKey, (Boolean) null)).thenReturn(null);

            boolean result = mcpServiceFilter.shouldExposeAsMcpTool(providerModel);
            assertTrue(result);
        }
    }

    @Test
    void testShouldExposeAsMcpTool_NotIncludedByPattern() {
        try (MockedStatic<org.apache.dubbo.common.config.ConfigurationUtils> configUtils =
                mockStatic(org.apache.dubbo.common.config.ConfigurationUtils.class)) {

            configUtils
                    .when(() -> org.apache.dubbo.common.config.ConfigurationUtils.getGlobalConfiguration(any()))
                    .thenReturn(configuration);

            when(configuration.getBoolean(McpConstant.SETTINGS_MCP_DEFAULT_ENABLED, true))
                    .thenReturn(true);
            when(configuration.getString(McpConstant.SETTINGS_MCP_INCLUDE_PATTERNS, ""))
                    .thenReturn("com.api.*");
            when(configuration.getString(McpConstant.SETTINGS_MCP_EXCLUDE_PATTERNS, ""))
                    .thenReturn("");

            mcpServiceFilter = new McpServiceFilter(applicationModel);

            when(providerModel.getServiceModel()).thenReturn(serviceDescriptor);
            when(serviceDescriptor.getInterfaceName()).thenReturn("com.example.TestService");
            when(providerModel.getServiceInstance()).thenReturn(null);

            boolean result = mcpServiceFilter.shouldExposeAsMcpTool(providerModel);
            assertFalse(result);
        }
    }

    @Test
    void testGetMcpToolConfig() {
        try (MockedStatic<org.apache.dubbo.common.config.ConfigurationUtils> configUtils =
                mockStatic(org.apache.dubbo.common.config.ConfigurationUtils.class)) {

            configUtils
                    .when(() -> org.apache.dubbo.common.config.ConfigurationUtils.getGlobalConfiguration(any()))
                    .thenReturn(configuration);

            when(configuration.getBoolean(McpConstant.SETTINGS_MCP_DEFAULT_ENABLED, true))
                    .thenReturn(false);
            when(configuration.getString(McpConstant.SETTINGS_MCP_INCLUDE_PATTERNS, ""))
                    .thenReturn("");
            when(configuration.getString(McpConstant.SETTINGS_MCP_EXCLUDE_PATTERNS, ""))
                    .thenReturn("");

            mcpServiceFilter = new McpServiceFilter(applicationModel);

            @DubboService(
                    mcpToolName = "test-tool",
                    mcpDescription = "Test service description",
                    mcpTags = {"test", "demo"})
            class TestService {}

            TestService serviceInstance = new TestService();

            when(providerModel.getServiceModel()).thenReturn(serviceDescriptor);
            when(serviceDescriptor.getInterfaceName()).thenReturn("com.example.TestService");
            when(providerModel.getServiceInstance()).thenReturn(serviceInstance);

            McpServiceFilter.McpToolConfig config = mcpServiceFilter.getMcpToolConfig(providerModel);

            assertEquals("test-tool", config.getToolName());
            assertEquals("Test service description", config.getDescription());
            assertEquals(2, config.getTags().size());
            assertTrue(config.getTags().contains("test"));
            assertTrue(config.getTags().contains("demo"));
        }
    }

    @Test
    void testGetMcpToolConfig_WithConfigOverride() {
        try (MockedStatic<org.apache.dubbo.common.config.ConfigurationUtils> configUtils =
                mockStatic(org.apache.dubbo.common.config.ConfigurationUtils.class)) {

            configUtils
                    .when(() -> org.apache.dubbo.common.config.ConfigurationUtils.getGlobalConfiguration(any()))
                    .thenReturn(configuration);

            when(configuration.getBoolean(McpConstant.SETTINGS_MCP_DEFAULT_ENABLED, true))
                    .thenReturn(false);
            when(configuration.getString(McpConstant.SETTINGS_MCP_INCLUDE_PATTERNS, ""))
                    .thenReturn("");
            when(configuration.getString(McpConstant.SETTINGS_MCP_EXCLUDE_PATTERNS, ""))
                    .thenReturn("");

            mcpServiceFilter = new McpServiceFilter(applicationModel);

            // Setup test data
            when(providerModel.getServiceModel()).thenReturn(serviceDescriptor);
            when(serviceDescriptor.getInterfaceName()).thenReturn("com.example.TestService");
            when(providerModel.getServiceInstance()).thenReturn(null);

            String servicePrefix = McpConstant.SETTINGS_MCP_SERVICE_PREFIX + ".com.example.TestService.";
            when(configuration.getString(servicePrefix + McpConstant.SETTINGS_MCP_SERVICE_NAME_SUFFIX))
                    .thenReturn("custom-tool-name");
            when(configuration.getString(servicePrefix + McpConstant.SETTINGS_MCP_SERVICE_DESCRIPTION_SUFFIX))
                    .thenReturn("Custom description");
            when(configuration.getString(servicePrefix + McpConstant.SETTINGS_MCP_SERVICE_TAGS_SUFFIX))
                    .thenReturn("tag1,tag2,tag3");

            McpServiceFilter.McpToolConfig config = mcpServiceFilter.getMcpToolConfig(providerModel);

            assertEquals("custom-tool-name", config.getToolName());
            assertEquals("Custom description", config.getDescription());
            assertEquals(3, config.getTags().size());
            assertTrue(config.getTags().contains("tag1"));
            assertTrue(config.getTags().contains("tag2"));
            assertTrue(config.getTags().contains("tag3"));
        }
    }
}
