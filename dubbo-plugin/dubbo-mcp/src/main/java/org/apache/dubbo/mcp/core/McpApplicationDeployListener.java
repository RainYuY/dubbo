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
import org.apache.dubbo.common.config.ConfigurationUtils;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.constants.LoggerCodeConstants;
import org.apache.dubbo.common.deploy.ApplicationDeployListener;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.threadpool.manager.FrameworkExecutorRepository;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.builders.InternalServiceConfigBuilder;
import org.apache.dubbo.mcp.McpConstant;
import org.apache.dubbo.mcp.tool.DubboMcpGenericCaller;
import org.apache.dubbo.mcp.tool.DubboOpenApiToolConverter;
import org.apache.dubbo.mcp.tool.DubboServiceToolRegistry;
import org.apache.dubbo.mcp.transport.DubboMcpSseTransportProvider;
import org.apache.dubbo.mcp.transport.DubboMcpStreamableTransportProvider;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.FrameworkModel;
import org.apache.dubbo.rpc.model.ProviderModel;
import org.apache.dubbo.rpc.protocol.tri.rest.openapi.DefaultOpenAPIService;

import java.util.Collection;
import java.util.concurrent.ExecutorService;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.server.McpAsyncServer;
import io.modelcontextprotocol.server.McpServer;
import io.modelcontextprotocol.spec.McpSchema;

import static org.apache.dubbo.metadata.util.MetadataServiceVersionUtils.V1;

public class McpApplicationDeployListener implements ApplicationDeployListener {

    private static final ErrorTypeAwareLogger logger =
            LoggerFactory.getErrorTypeAwareLogger(McpApplicationDeployListener.class);
    private DubboServiceToolRegistry toolRegistry;
    private McpServiceFilter mcpServiceFilter;
    private boolean mcpEnable = true;

    private volatile ServiceConfig<McpService> serviceConfig;

    private static DubboMcpSseTransportProvider dubboMcpSseTransportProvider;

    private static DubboMcpStreamableTransportProvider dubboMcpStreamableTransportProvider;

    private McpAsyncServer mcpAsyncServer;

    @Override
    public void onInitialize(ApplicationModel scopeModel) {}

    @Override
    public void onStarting(ApplicationModel applicationModel) {}

    public static DubboMcpSseTransportProvider getDubboMcpSseTransportProvider() {
        return dubboMcpSseTransportProvider;
    }

    public static DubboMcpStreamableTransportProvider getDubboMcpStreamableTransportProvider() {
        return dubboMcpStreamableTransportProvider;
    }

    @Override
    public void onStarted(ApplicationModel applicationModel) {
        Configuration globalConf = ConfigurationUtils.getGlobalConfiguration(applicationModel);
        mcpEnable = globalConf.getBoolean(McpConstant.SETTINGS_MCP_ENABLE, true);
        if (!mcpEnable) {
            logger.info("MCP service is disabled, skipping initialization");
            return;
        }
        try {
            logger.info("Initializing MCP server and dynamic service registration");

            // Initialize service filter
            mcpServiceFilter = new McpServiceFilter(applicationModel);

            dubboMcpSseTransportProvider = new DubboMcpSseTransportProvider(new ObjectMapper());
            dubboMcpStreamableTransportProvider = new DubboMcpStreamableTransportProvider(new ObjectMapper());
            McpSchema.ServerCapabilities.ToolCapabilities toolCapabilities =
                    new McpSchema.ServerCapabilities.ToolCapabilities(true);
            McpSchema.ServerCapabilities serverCapabilities =
                    new McpSchema.ServerCapabilities(null, null, null, null, toolCapabilities);

            mcpAsyncServer = McpServer.async(dubboMcpStreamableTransportProvider)
                    .capabilities(serverCapabilities)
                    .build();

            FrameworkModel frameworkModel = applicationModel.getFrameworkModel();
            DefaultOpenAPIService defaultOpenAPIService = new DefaultOpenAPIService(frameworkModel);

            DubboOpenApiToolConverter toolConverter = new DubboOpenApiToolConverter(defaultOpenAPIService);

            DubboMcpGenericCaller genericCaller = new DubboMcpGenericCaller(applicationModel);

            toolRegistry = new DubboServiceToolRegistry(mcpAsyncServer, toolConverter, genericCaller, mcpServiceFilter);

            applicationModel.getBeanFactory().registerBean(toolRegistry);

            Collection<ProviderModel> providerModels =
                    applicationModel.getApplicationServiceRepository().allProviderModels();

            int registeredCount = 0;
            for (ProviderModel pm : providerModels) {
                int serviceRegisteredCount = toolRegistry.registerService(pm);
                registeredCount += serviceRegisteredCount;
            }

            exportMcpService(applicationModel);
            logger.info(
                    "MCP server initialized successfully, {} existing tools registered, dynamic registration enabled",
                    registeredCount);
        } catch (Exception e) {
            logger.error(
                    LoggerCodeConstants.COMMON_UNEXPECTED_EXCEPTION,
                    "",
                    "",
                    "MCP service initialization failed: " + e.getMessage(),
                    e);
        }
    }

    @Override
    public void onStopping(ApplicationModel applicationModel) {
        if (toolRegistry != null) {
            logger.info("MCP server stopping, clearing tool registry");
            toolRegistry.clearRegistry();
        }
    }

    @Override
    public void onStopped(ApplicationModel applicationModel) {
        if (mcpEnable && mcpAsyncServer != null) {
            mcpAsyncServer.close();
        }
    }

    @Override
    public void onFailure(ApplicationModel applicationModel, Throwable cause) {}

    private void exportMcpService(ApplicationModel applicationModel) {
        McpServiceImpl mcpSseServiceImpl = applicationModel.getBeanFactory().getOrRegisterBean(McpServiceImpl.class);

        ExecutorService internalServiceExecutor = applicationModel
                .getFrameworkModel()
                .getBeanFactory()
                .getBean(FrameworkExecutorRepository.class)
                .getInternalServiceExecutor();

        this.serviceConfig = InternalServiceConfigBuilder.<McpService>newBuilder(applicationModel)
                .interfaceClass(McpService.class)
                .protocol(CommonConstants.TRIPLE, McpConstant.MCP_SERVICE_PROTOCOL)
                .port(getRegisterPort(), String.valueOf(McpConstant.MCP_SERVICE_PORT))
                .registryId("internal-mcp-registry")
                .executor(internalServiceExecutor)
                .ref(mcpSseServiceImpl)
                .version(V1)
                .build();
        serviceConfig.export();
        logger.info("MCP service exported on: {}", serviceConfig.getExportedUrls());
    }

    /**
     * Get the Mcp service register port.
     * First, try to get config from user configuration, if not found, get from protocol config.
     * Second, try to get config from protocol config, if not found, get a random available port.
     */
    private int getRegisterPort() {
        Configuration globalConf = ConfigurationUtils.getGlobalConfiguration(ApplicationModel.defaultModel());
        int mcpPort = globalConf.getInt(McpConstant.SETTINGS_MCP_PORT, -1);
        if (mcpPort != -1) {
            return mcpPort;
        }
        ApplicationModel applicationModel = ApplicationModel.defaultModel();
        Collection<ProtocolConfig> protocolConfigs =
                applicationModel.getApplicationConfigManager().getProtocols();
        if (CollectionUtils.isNotEmpty(protocolConfigs)) {
            for (ProtocolConfig protocolConfig : protocolConfigs) {
                if (CommonConstants.TRIPLE.equals(protocolConfig.getName())) {
                    return protocolConfig.getPort();
                }
            }
        }
        return NetUtils.getAvailablePort();
    }
}
