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
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.StringUtils;
import org.apache.dubbo.config.annotation.DubboService;
import org.apache.dubbo.mcp.McpConstant;
import org.apache.dubbo.rpc.model.ApplicationModel;
import org.apache.dubbo.rpc.model.ProviderModel;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

public class McpServiceFilter {

    private static final ErrorTypeAwareLogger logger = LoggerFactory.getErrorTypeAwareLogger(McpServiceFilter.class);

    private final Configuration configuration;
    private final Pattern[] includePatterns;
    private final Pattern[] excludePatterns;
    private final boolean defaultEnabled;

    public McpServiceFilter(ApplicationModel applicationModel) {
        this.configuration = ConfigurationUtils.getGlobalConfiguration(applicationModel);
        this.defaultEnabled = configuration.getBoolean(McpConstant.SETTINGS_MCP_DEFAULT_ENABLED, true);

        // Parse include and exclude patterns
        String includeStr = configuration.getString(McpConstant.SETTINGS_MCP_INCLUDE_PATTERNS, "");
        String excludeStr = configuration.getString(McpConstant.SETTINGS_MCP_EXCLUDE_PATTERNS, "");

        this.includePatterns = parsePatterns(includeStr);
        this.excludePatterns = parsePatterns(excludeStr);

        logger.debug(
                "MCP service filter initialized: defaultEnabled={}, includePatterns={}, excludePatterns={}",
                defaultEnabled,
                includeStr,
                excludeStr);
    }

    /**
     * Check if service should be exposed as MCP tool
     */
    public boolean shouldExposeAsMcpTool(ProviderModel providerModel) {
        String interfaceName = providerModel.getServiceModel().getInterfaceName();

        // 1. Check exclude patterns (the highest priority)
        if (isMatchedByPatterns(interfaceName, excludePatterns)) {
            return false;
        }

        // 2. Check annotation configuration
        Object serviceBean = providerModel.getServiceInstance();
        if (serviceBean != null) {
            DubboService dubboService = serviceBean.getClass().getAnnotation(DubboService.class);
            if (dubboService != null && dubboService.mcpEnabled()) {
                return true;
            }
        }

        // 3. Check specific service configuration
        String serviceSpecificKey = McpConstant.SETTINGS_MCP_SERVICE_PREFIX + "." + interfaceName + "."
                + McpConstant.SETTINGS_MCP_SERVICE_ENABLED_SUFFIX;
        Boolean configEnabled = configuration.getBoolean(serviceSpecificKey, (Boolean) null);
        if (configEnabled != null) {
            return configEnabled;
        }

        // 4. Check include patterns
        if (includePatterns.length > 0) {
            // If include patterns are defined, only services matching them should be included
            return isMatchedByPatterns(interfaceName, includePatterns);
        }

        // 5. Use default configuration
        return defaultEnabled;
    }

    /**
     * Get MCP tool configuration for service
     */
    public McpToolConfig getMcpToolConfig(ProviderModel providerModel) {
        String interfaceName = providerModel.getServiceModel().getInterfaceName();
        McpToolConfig config = new McpToolConfig();

        // Get configuration from annotation
        Object serviceBean = providerModel.getServiceInstance();
        if (serviceBean != null) {
            DubboService dubboService = serviceBean.getClass().getAnnotation(DubboService.class);
            if (dubboService != null) {
                config.setToolName(dubboService.mcpToolName());
                config.setDescription(dubboService.mcpDescription());
                config.setTags(Arrays.asList(dubboService.mcpTags()));
            }
        }

        // Get configuration from config file (higher priority)
        String servicePrefix = McpConstant.SETTINGS_MCP_SERVICE_PREFIX + "." + interfaceName + ".";

        String configToolName = configuration.getString(servicePrefix + McpConstant.SETTINGS_MCP_SERVICE_NAME_SUFFIX);
        if (StringUtils.isNotEmpty(configToolName)) {
            config.setToolName(configToolName);
        }

        String configDescription =
                configuration.getString(servicePrefix + McpConstant.SETTINGS_MCP_SERVICE_DESCRIPTION_SUFFIX);
        if (StringUtils.isNotEmpty(configDescription)) {
            config.setDescription(configDescription);
        }

        String configTags = configuration.getString(servicePrefix + McpConstant.SETTINGS_MCP_SERVICE_TAGS_SUFFIX);
        if (StringUtils.isNotEmpty(configTags)) {
            config.setTags(Arrays.asList(configTags.split(",")));
        }

        return config;
    }

    private Pattern[] parsePatterns(String patternStr) {
        if (StringUtils.isEmpty(patternStr)) {
            return new Pattern[0];
        }

        return Arrays.stream(patternStr.split(","))
                .map(String::trim)
                .filter(StringUtils::isNotEmpty)
                .map(pattern -> Pattern.compile(pattern.replace("*", ".*")))
                .toArray(Pattern[]::new);
    }

    private boolean isMatchedByPatterns(String text, Pattern[] patterns) {
        for (Pattern pattern : patterns) {
            if (pattern.matcher(text).matches()) {
                return true;
            }
        }
        return false;
    }

    /**
     * MCP tool configuration
     */
    public static class McpToolConfig {
        private String toolName;
        private String description;
        private List<String> tags;

        public String getToolName() {
            return toolName;
        }

        public void setToolName(String toolName) {
            this.toolName = toolName;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }

        public List<String> getTags() {
            return tags;
        }

        public void setTags(List<String> tags) {
            this.tags = tags;
        }
    }
}
