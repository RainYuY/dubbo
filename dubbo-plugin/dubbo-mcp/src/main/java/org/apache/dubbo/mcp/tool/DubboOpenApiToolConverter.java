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
package org.apache.dubbo.mcp.tool;

import org.apache.dubbo.common.URL;
import org.apache.dubbo.common.constants.LoggerCodeConstants;
import org.apache.dubbo.common.logger.ErrorTypeAwareLogger;
import org.apache.dubbo.common.logger.LoggerFactory;
import org.apache.dubbo.common.utils.PojoUtils;
import org.apache.dubbo.mcp.core.McpServiceFilter;
import org.apache.dubbo.remoting.http12.HttpMethods;
import org.apache.dubbo.remoting.http12.rest.OpenAPIRequest;
import org.apache.dubbo.remoting.http12.rest.ParamType;
import org.apache.dubbo.rpc.model.ServiceDescriptor;
import org.apache.dubbo.rpc.protocol.tri.rest.mapping.meta.MethodMeta;
import org.apache.dubbo.rpc.protocol.tri.rest.mapping.meta.ParameterMeta;
import org.apache.dubbo.rpc.protocol.tri.rest.openapi.DefaultOpenAPIService;
import org.apache.dubbo.rpc.protocol.tri.rest.openapi.model.OpenAPI;
import org.apache.dubbo.rpc.protocol.tri.rest.openapi.model.Operation;
import org.apache.dubbo.rpc.protocol.tri.rest.openapi.model.Parameter;
import org.apache.dubbo.rpc.protocol.tri.rest.openapi.model.PathItem;
import org.apache.dubbo.rpc.protocol.tri.rest.openapi.model.Schema;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.modelcontextprotocol.spec.McpSchema;

public class DubboOpenApiToolConverter {

    private static final ErrorTypeAwareLogger logger =
            LoggerFactory.getErrorTypeAwareLogger(DubboOpenApiToolConverter.class);
    private final DefaultOpenAPIService openApiService;
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, Operation> opCache = new ConcurrentHashMap<>();

    public DubboOpenApiToolConverter(DefaultOpenAPIService openApiService) {
        this.openApiService = openApiService;
    }

    public Map<String, McpSchema.Tool> convertToTools(ServiceDescriptor svcDesc, URL svcUrl) {
        return convertToTools(svcDesc, svcUrl, null);
    }

    public Map<String, McpSchema.Tool> convertToTools(
            ServiceDescriptor svcDesc, URL svcUrl, McpServiceFilter.McpToolConfig toolConfig) {
        opCache.clear(); // Clear cache for each new service conversion context

        OpenAPIRequest req = new OpenAPIRequest();
        String intfName = svcDesc.getInterfaceName();
        req.setService(new String[] {intfName});

        OpenAPI openApiDef = openApiService.getOpenAPI(req);

        if (openApiDef == null || openApiDef.getPaths() == null) {
            logger.debug("OpenAPI definition or paths are null for service: {}", intfName);
            return new HashMap<>();
        }

        Map<String, McpSchema.Tool> mcpTools = new HashMap<>();
        for (Map.Entry<String, PathItem> pathEntry : openApiDef.getPaths().entrySet()) {
            String path = pathEntry.getKey();
            PathItem item = pathEntry.getValue();
            if (item.getOperations() != null) {
                for (Map.Entry<HttpMethods, Operation> opEntry :
                        item.getOperations().entrySet()) {
                    HttpMethods httpMethod = opEntry.getKey();
                    Operation op = opEntry.getValue();
                    if (op == null || op.getOperationId() == null) {
                        logger.debug("Skipping operation without ID for path {} and HTTP method {}", path, httpMethod);
                        continue;
                    }
                    String opId = op.getOperationId();
                    McpSchema.Tool mcpTool = convertOperationToMcpTool(path, httpMethod, op, toolConfig);
                    mcpTools.put(opId, mcpTool);
                    opCache.put(opId, op); // Cache the original Operation object
                }
            }
        }
        return mcpTools;
    }

    public Operation getOperationByToolName(String toolName) {
        return opCache.get(toolName);
    }

    private McpSchema.Tool convertOperationToMcpTool(String path, HttpMethods method, Operation op) {
        return convertOperationToMcpTool(path, method, op, null);
    }

    private McpSchema.Tool convertOperationToMcpTool(
            String path, HttpMethods method, Operation op, McpServiceFilter.McpToolConfig toolConfig) {
        String opId = op.getOperationId();

        // Apply tool name configuration
        String toolName = opId;
        if (toolConfig != null
                && toolConfig.getToolName() != null
                && !toolConfig.getToolName().isEmpty()) {
            toolName = toolConfig.getToolName() + "_" + opId;
        }

        // Apply tool description configuration
        String desc = null;
        if (toolConfig != null
                && toolConfig.getDescription() != null
                && !toolConfig.getDescription().isEmpty()) {
            desc = toolConfig.getDescription();
        } else {
            desc = op.getSummary();
            if (desc == null || desc.isEmpty()) {
                desc = op.getDescription();
            }
            if (desc == null || desc.isEmpty()) {
                desc = "Executes operation '" + opId + "' which corresponds to a " + method.name() + " request on path "
                        + path + ".";
            }
        }

        Map<String, Object> paramsSchemaMap = extractParameterSchema(op);
        String schemaJson;
        try {
            schemaJson = objectMapper.writeValueAsString(paramsSchemaMap);
        } catch (Exception e) {
            logger.error(
                    LoggerCodeConstants.COMMON_UNEXPECTED_EXCEPTION,
                    "Failed to serialize parameter schema for tool {}: {}",
                    opId,
                    e.getMessage(),
                    e);
            schemaJson = "{\"type\":\"object\",\"properties\":{}}";
        }
        return new McpSchema.Tool(toolName, desc, schemaJson);
    }

    private Map<String, Object> extractParameterSchema(Operation op) {
        Map<String, Object> schema = new HashMap<>();
        Map<String, Object> props = new HashMap<>();
        schema.put("type", "object");

        // Handle path, query, header parameters first
        if (op.getParameters() != null) {
            for (Parameter apiParam : op.getParameters()) {
                if (apiParam.getName() != null && apiParam.getSchema() != null) {
                    // Pass the parameter name for potentially better default descriptions
                    props.put(
                            apiParam.getName(), convertOpenApiSchemaToMcpMap(apiParam.getSchema(), apiParam.getName()));
                }
            }
        }

        // Handle Request Body
        if (op.getRequestBody() != null && op.getRequestBody().getContents() != null) {
            op.getRequestBody().getContents().values().stream().findFirst().ifPresent(mediaType -> {
                if (mediaType.getSchema() != null) {
                    Schema bodySchema = mediaType.getSchema();
                    MethodMeta methodMeta = op.getMeta();
                    String inferredBodyParamName = "requestBodyPayload";

                    if (methodMeta != null && methodMeta.getParameters() != null) {
                        ParameterMeta[] methodParams = methodMeta.getParameters();
                        ParameterMeta requestBodyJavaParam = null;

                        if (methodParams.length == 1) {
                            ParameterMeta singleParam = methodParams[0];
                            if (singleParam.getNamedValueMeta().paramType() == null
                                    || singleParam.getNamedValueMeta().paramType() == ParamType.Body) {
                                requestBodyJavaParam = singleParam;
                            }
                        } else {
                            for (ParameterMeta pMeta : methodParams) {
                                if (pMeta.getNamedValueMeta().paramType() == ParamType.Body) {
                                    requestBodyJavaParam = pMeta;
                                    break;
                                }
                            }
                            if (requestBodyJavaParam == null) {
                                for (ParameterMeta pMeta : methodParams) {
                                    if (pMeta.getNamedValueMeta().paramType() == null
                                            && PojoUtils.isPojo(pMeta.getType())) {
                                        requestBodyJavaParam = pMeta;
                                        break;
                                    }
                                }
                            }
                        }

                        if (requestBodyJavaParam != null) {
                            String actualParamName = requestBodyJavaParam.getName();
                            if (actualParamName != null
                                    && !actualParamName.startsWith("arg")
                                    && !actualParamName.isEmpty()) {
                                inferredBodyParamName = actualParamName;

                            } else {
                                logger.warn(
                                        LoggerCodeConstants.COMMON_UNEXPECTED_EXCEPTION,
                                        "",
                                        "",
                                        "Operation '" + op.getOperationId()
                                                + "': Could not get a meaningful name for request body param from MethodMeta (name was '"
                                                + op.getOperationId() + "'). Using default '" + inferredBodyParamName
                                                + "'. Ensure '-parameters' compiler flag.");
                            }
                        } else {
                            logger.warn(
                                    LoggerCodeConstants.COMMON_UNEXPECTED_EXCEPTION,
                                    "",
                                    "",
                                    "Operation '" + op.getOperationId()
                                            + "': Could not identify a specific method parameter for the request body via MethodMeta. Using default name '"
                                            + inferredBodyParamName + " ' for schema type '" + bodySchema.getType()
                                            + "'");
                        }
                    } else {
                        logger.warn(
                                LoggerCodeConstants.COMMON_UNEXPECTED_EXCEPTION,
                                "",
                                "",
                                "Operation '" + op.getOperationId()
                                        + "': MethodMeta not available for request body parameter name inference. Using default name '"
                                        + inferredBodyParamName + "' for schema type '" + bodySchema.getType() + "'.");
                    }
                    props.put(inferredBodyParamName, convertOpenApiSchemaToMcpMap(bodySchema, inferredBodyParamName));
                }
            });
        }
        schema.put("properties", props);
        return schema;
    }

    // Overloaded method for cases where property name is not known or not applicable (e.g. array items)
    private Map<String, Object> convertOpenApiSchemaToMcpMap(Schema openApiSchema) {
        return convertOpenApiSchemaToMcpMap(openApiSchema, null);
    }

    private Map<String, Object> convertOpenApiSchemaToMcpMap(Schema openApiSchema, String propertyName) {
        Map<String, Object> mcpMap = new HashMap<>();
        if (openApiSchema == null) {
            return mcpMap;
        }

        if (openApiSchema.getRef() != null) {
            mcpMap.put("$ref", openApiSchema.getRef());
        }
        if (openApiSchema.getType() != null) {
            mcpMap.put("type", openApiSchema.getType().toString().toLowerCase());
        }
        if (openApiSchema.getFormat() != null) {
            mcpMap.put("format", openApiSchema.getFormat());
        }

        if (openApiSchema.getDescription() != null
                && !openApiSchema.getDescription().isEmpty()) {
            mcpMap.put("description", openApiSchema.getDescription());
        } else {
            String defaultParamDesc = getParamDesc(openApiSchema, propertyName);

            mcpMap.put("description", defaultParamDesc);
        }

        if (openApiSchema.getEnumeration() != null
                && !openApiSchema.getEnumeration().isEmpty()) {
            mcpMap.put("enum", openApiSchema.getEnumeration());
        }
        if (openApiSchema.getDefaultValue() != null) {
            mcpMap.put("default", openApiSchema.getDefaultValue());
        }

        if (Schema.Type.OBJECT.equals(openApiSchema.getType()) && openApiSchema.getProperties() != null) {
            Map<String, Object> nestedProps = new HashMap<>();
            openApiSchema
                    .getProperties()
                    .forEach((name, propSchema) ->
                            // Pass the nested property name for its default description generation
                            nestedProps.put(name, convertOpenApiSchemaToMcpMap(propSchema, name)));
            mcpMap.put("properties", nestedProps);
        }

        if (Schema.Type.ARRAY.equals(openApiSchema.getType()) && openApiSchema.getItems() != null) {
            // For array items, the 'propertyName' context is less direct, so passing null or a generic placeholder
            mcpMap.put(
                    "items",
                    convertOpenApiSchemaToMcpMap(
                            openApiSchema.getItems(), propertyName != null ? propertyName + "_item" : null));
        }
        return mcpMap;
    }

    private static String getParamDesc(Schema openApiSchema, String propertyName) {
        String typeOrRefString = "";
        if (openApiSchema.getRef() != null && !openApiSchema.getRef().isEmpty()) {
            String ref = openApiSchema.getRef();
            String componentName = ref.substring(ref.lastIndexOf('/') + 1);
            typeOrRefString = " referencing '" + componentName + "'"; // Indicates it's a reference
            if (openApiSchema.getType() != null) {
                typeOrRefString += " (which is of type '"
                        + openApiSchema.getType().toString().toLowerCase() + "')";
            }

        } else if (openApiSchema.getType() != null) {
            typeOrRefString = " of type '" + openApiSchema.getType().toString().toLowerCase() + "'";
            if (openApiSchema.getFormat() != null) {
                typeOrRefString += " with format '" + openApiSchema.getFormat() + "'";
            }
        }

        String namePrefix;
        if (propertyName != null && !propertyName.isEmpty()) {
            namePrefix = "Parameter '" + propertyName + "'";
        } else {
            namePrefix = typeOrRefString.isEmpty() ? "Parameter" : "Schema";
        }

        return namePrefix + typeOrRefString + ".";
    }
}
