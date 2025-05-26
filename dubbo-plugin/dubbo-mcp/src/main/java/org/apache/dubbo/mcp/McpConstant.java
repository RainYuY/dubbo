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
package org.apache.dubbo.mcp;

public interface McpConstant {

    String SETTINGS_MCP_PREFIX = "dubbo.protocol.triple.rest.mcp";
    String SETTINGS_MCP_ENABLE = "dubbo.protocol.triple.rest.mcp.enabled";
    String SETTINGS_MCP_PORT = "dubbo.protocol.triple.rest.mcp.port";
    String SETTINGS_MCP_PATHS_SSE = "dubbo.protocol.triple.rest.mcp.path.sse";
    String SETTINGS_MCP_PATHS_MESSAGE = "dubbo.protocol.triple.rest.mcp.path.message";

    // MCP 服务控制相关配置
    String SETTINGS_MCP_SERVICE_PREFIX = "dubbo.protocol.triple.rest.mcp.service";
    String SETTINGS_MCP_SERVICE_ENABLED_SUFFIX = "enabled";
    String SETTINGS_MCP_SERVICE_NAME_SUFFIX = "tool-name";
    String SETTINGS_MCP_SERVICE_DESCRIPTION_SUFFIX = "description";
    String SETTINGS_MCP_SERVICE_TAGS_SUFFIX = "tags";

    // 全局默认配置
    String SETTINGS_MCP_DEFAULT_ENABLED = "dubbo.protocol.triple.rest.mcp.default.enabled";
    String SETTINGS_MCP_INCLUDE_PATTERNS = "dubbo.protocol.triple.rest.mcp.include.patterns";
    String SETTINGS_MCP_EXCLUDE_PATTERNS = "dubbo.protocol.triple.rest.mcp.exclude.patterns";

    String MCP_SERVICE_PROTOCOL = "mcp-service-protocol";
    String MCP_SERVICE_PORT = "mcp-service-port";
}
