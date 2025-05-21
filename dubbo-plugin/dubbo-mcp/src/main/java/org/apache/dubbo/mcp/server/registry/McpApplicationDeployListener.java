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
package org.apache.dubbo.mcp.server.registry;

import org.apache.dubbo.common.config.Configuration;
import org.apache.dubbo.common.config.ConfigurationUtils;
import org.apache.dubbo.common.constants.CommonConstants;
import org.apache.dubbo.common.deploy.ApplicationDeployListener;
import org.apache.dubbo.common.logger.Logger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.threadpool.manager.FrameworkExecutorRepository;
import org.apache.dubbo.common.utils.CollectionUtils;
import org.apache.dubbo.common.utils.NetUtils;
import org.apache.dubbo.config.ProtocolConfig;
import org.apache.dubbo.config.ServiceConfig;
import org.apache.dubbo.config.bootstrap.builders.InternalServiceConfigBuilder;
import org.apache.dubbo.mcp.server.McpConstant;
import org.apache.dubbo.mcp.server.McpSseService;
import org.apache.dubbo.mcp.server.McpSseServiceImpl;
import org.apache.dubbo.mcp.server.generic.DubboMcpGenericCaller;
import org.apache.dubbo.mcp.server.transport.DubboMcpSseTransportProvider;
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

    private static final Logger logger = LoggerFactory.getLogger(McpApplicationDeployListener.class);
    private DubboServiceToolRegistry toolRegistry;
    private boolean mcpEnable = true;

    private volatile ServiceConfig<McpSseService> serviceConfig;

    private static DubboMcpSseTransportProvider dubboMcpSseTransportProvider;

    @Override
    public void onInitialize(ApplicationModel scopeModel) {}

    @Override
    public void onStarting(ApplicationModel applicationModel) {}

    public static DubboMcpSseTransportProvider getDubboMcpSseTransportProvider() {
        return dubboMcpSseTransportProvider;
    }

    @Override
    public void onStarted(ApplicationModel applicationModel) {
        Configuration globalConf = ConfigurationUtils.getGlobalConfiguration(ApplicationModel.defaultModel());
        mcpEnable = globalConf.getBoolean(McpConstant.SETTINGS_MCP_ENABLE, true);
        if (mcpEnable) {
            logger.debug("McpApplicationDeployListener: MCP service is enabled.");
        } else {
            logger.debug("McpApplicationDeployListener: MCP service is disabled. Skipping initialization.");
            return;
        }
        try {
            logger.info("McpApplicationDeployListener: Application started. Initializing MCP server and tools...");

            dubboMcpSseTransportProvider = new DubboMcpSseTransportProvider(new ObjectMapper());
            McpSchema.ServerCapabilities.ToolCapabilities toolCapabilities =
                    new McpSchema.ServerCapabilities.ToolCapabilities(true);
            McpSchema.ServerCapabilities serverCapabilities =
                    new McpSchema.ServerCapabilities(null, null, null, null, toolCapabilities);

            McpAsyncServer mcpAsyncServer = McpServer.async(dubboMcpSseTransportProvider)
                    .capabilities(serverCapabilities)
                    .build();

            FrameworkModel frameworkModel = applicationModel.getFrameworkModel();
            DefaultOpenAPIService defaultOpenAPIService = new DefaultOpenAPIService(frameworkModel);

            DubboOpenApiToolConverter toolConverter = new DubboOpenApiToolConverter(defaultOpenAPIService);

            DubboMcpGenericCaller genericCaller = new DubboMcpGenericCaller(applicationModel);

            toolRegistry = new DubboServiceToolRegistry(mcpAsyncServer, toolConverter, genericCaller);

            Collection<ProviderModel> providerModels =
                    applicationModel.getApplicationServiceRepository().allProviderModels();
            logger.info("Found " + providerModels.size() + " provider models. Starting tool registration...");
            for (ProviderModel pm : providerModels) {
                logger.info("Processing ProviderModel: " + pm.getServiceKey() + ", module: "
                        + pm.getModuleModel().getDesc());
                toolRegistry.registerService(pm);
            }
            exportMcpService(applicationModel);
            logger.info("MCP service initialization complete. Registered " + toolRegistry.getRegisteredToolCount()
                    + " tools.");
        } catch (Exception e) {
            logger.error("MCP service initialization failed: " + e.getMessage(), e);
        }
    }

    @Override
    public void onStopping(ApplicationModel applicationModel) {
        if (toolRegistry != null) {
            logger.info("MCP service stopping. Clearing tool registry...");
            toolRegistry.clearRegistry();
        }
    }

    @Override
    public void onStopped(ApplicationModel applicationModel) {
        if (mcpEnable && dubboMcpSseTransportProvider != null) {
            // TODO: close the mcp server
        }
    }

    @Override
    public void onFailure(ApplicationModel applicationModel, Throwable cause) {}

    private void exportMcpService(ApplicationModel applicationModel) {
        McpSseServiceImpl mcpSseServiceImpl =
                applicationModel.getBeanFactory().getOrRegisterBean(McpSseServiceImpl.class);

        ExecutorService internalServiceExecutor = applicationModel
                .getFrameworkModel()
                .getBeanFactory()
                .getBean(FrameworkExecutorRepository.class)
                .getInternalServiceExecutor();

        this.serviceConfig = InternalServiceConfigBuilder.<McpSseService>newBuilder(applicationModel)
                .interfaceClass(McpSseService.class)
                .protocol(CommonConstants.TRIPLE, McpConstant.MCP_SERVICE_PROTOCOL)
                .port(getRegisterPort(), McpConstant.MCP_SERVICE_PORT)
                .registryId("internal-mcp-registry")
                .executor(internalServiceExecutor)
                .ref(mcpSseServiceImpl)
                .version(V1)
                .build();
        serviceConfig.export();
        logger.info("The MCP service exports urls : " + serviceConfig.getExportedUrls());
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
