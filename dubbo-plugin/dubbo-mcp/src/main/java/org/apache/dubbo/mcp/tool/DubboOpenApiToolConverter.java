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
import org.apache.dubbo.mcp.JsonSchemaType;
import org.apache.dubbo.mcp.McpConstant;
import org.apache.dubbo.mcp.core.McpServiceFilter;
import org.apache.dubbo.mcp.util.TypeSchemaUtils;
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

                    McpServiceFilter.McpToolConfig methodConfig = getMethodConfig(op, toolConfig);

                    McpSchema.Tool mcpTool = convertOperationToMcpTool(path, httpMethod, op, methodConfig);
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

    private McpSchema.Tool convertOperationToMcpTool(
            String path, HttpMethods method, Operation op, McpServiceFilter.McpToolConfig toolConfig) {
        String opId = op.getOperationId();

        String toolName = generateToolName(op, toolConfig);

        String desc = generateToolDescription(op, toolConfig, path, method);

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

    /**
     * Generate a unique and meaningful tool name
     */
    private String generateToolName(Operation op, McpServiceFilter.McpToolConfig toolConfig) {
        String opId = op.getOperationId();

        if (toolConfig != null
                && toolConfig.getToolName() != null
                && !toolConfig.getToolName().isEmpty()) {
            return toolConfig.getToolName();
        }

        return opId;
    }

    /**
     * Generate tool description
     */
    private String generateToolDescription(
            Operation op, McpServiceFilter.McpToolConfig toolConfig, String path, HttpMethods method) {
        if (toolConfig != null
                && toolConfig.getDescription() != null
                && !toolConfig.getDescription().isEmpty()) {
            return toolConfig.getDescription();
        }

        String desc = op.getSummary();
        if (desc == null || desc.isEmpty()) {
            desc = op.getDescription();
        }
        if (desc == null || desc.isEmpty()) {
            desc = "Executes operation '" + op.getOperationId() + "' which corresponds to a " + method.name()
                    + " request on path " + path + ".";
        }

        return desc;
    }

    private Map<String, Object> extractParameterSchema(Operation op) {
        Map<String, Object> schema = new HashMap<>();
        Map<String, Object> props = new HashMap<>();
        schema.put(McpConstant.SCHEMA_PROPERTY_TYPE, JsonSchemaType.OBJECT_SCHEMA.getJsonSchemaType());

        // Handle path, query, header parameters first
        if (op.getParameters() != null) {
            for (Parameter apiParam : op.getParameters()) {
                if (McpConstant.PARAM_TRIPLE_SERVICE_GROUP.equals(apiParam.getName())) {
                    continue;
                }
                if (apiParam.getSchema() != null) {
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

                    if (methodMeta != null && methodMeta.getParameters() != null) {
                        ParameterMeta[] methodParams = methodMeta.getParameters();

                        // Handle multiple primitive parameters
                        // Check if we have multiple parameters with primitive types that should be treated individually
                        boolean shouldCreateIndividualParams =
                                methodParams.length > 1 && allParamsArePrimitive(methodParams);

                        if (shouldCreateIndividualParams) {
                            // For multiple primitive parameters, create individual properties
                            for (int i = 0; i < methodParams.length; i++) {
                                ParameterMeta param = methodParams[i];
                                String paramName = param.getName();

                                // Use meaningful parameter name or fallback to positional name
                                if (paramName == null || paramName.startsWith(McpConstant.DEFAULT_TOOL_NAME_PREFIX)) {
                                    paramName = param.getType().getSimpleName().toLowerCase() + "_" + (i + 1);
                                    logger.warn(
                                            LoggerCodeConstants.COMMON_UNEXPECTED_EXCEPTION,
                                            "",
                                            "",
                                            "Operation '" + op.getOperationId()
                                                    + "': Parameter " + i + " has default name '" + param.getName()
                                                    + "', using generated name '" + paramName
                                                    + "'. Ensure '-parameters' compiler flag is enabled.");
                                }

                                // Create schema for this parameter based on its Java type
                                Map<String, Object> paramSchema = new HashMap<>();
                                Class<?> paramType = param.getType();

                                if (paramType == Double.class || paramType == double.class) {
                                    paramSchema.put(
                                            McpConstant.SCHEMA_PROPERTY_TYPE,
                                            JsonSchemaType.NUMBER_SCHEMA.getJsonSchemaType());
                                    paramSchema.put(
                                            McpConstant.SCHEMA_PROPERTY_DESCRIPTION,
                                            McpConstant.PARAM_DESCRIPTION_DOUBLE);
                                } else if (paramType == Integer.class || paramType == int.class) {
                                    paramSchema.put(
                                            McpConstant.SCHEMA_PROPERTY_TYPE,
                                            JsonSchemaType.INTEGER_SCHEMA.getJsonSchemaType());
                                    paramSchema.put(
                                            McpConstant.SCHEMA_PROPERTY_DESCRIPTION,
                                            McpConstant.PARAM_DESCRIPTION_INTEGER);
                                } else if (paramType == String.class) {
                                    paramSchema.put(
                                            McpConstant.SCHEMA_PROPERTY_TYPE,
                                            JsonSchemaType.STRING_SCHEMA.getJsonSchemaType());
                                    paramSchema.put(
                                            McpConstant.SCHEMA_PROPERTY_DESCRIPTION,
                                            McpConstant.PARAM_DESCRIPTION_STRING);
                                } else if (paramType == Boolean.class || paramType == boolean.class) {
                                    paramSchema.put(
                                            McpConstant.SCHEMA_PROPERTY_TYPE,
                                            JsonSchemaType.BOOLEAN_SCHEMA.getJsonSchemaType());
                                    paramSchema.put(
                                            McpConstant.SCHEMA_PROPERTY_DESCRIPTION,
                                            McpConstant.PARAM_DESCRIPTION_BOOLEAN);
                                } else {
                                    // For complex types, use the OpenAPI schema
                                    paramSchema = convertOpenApiSchemaToMcpMap(bodySchema.getItems(), paramName);
                                }

                                props.put(paramName, paramSchema);
                            }
                        } else {
                            // Handle single parameter or POJO case (existing logic)
                            String inferredBodyParamName = McpConstant.PARAM_REQUEST_BODY_PAYLOAD;
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
                                        && !actualParamName.startsWith(McpConstant.DEFAULT_TOOL_NAME_PREFIX)
                                        && !actualParamName.isEmpty()) {
                                    inferredBodyParamName = actualParamName;

                                } else {
                                    logger.warn(
                                            LoggerCodeConstants.COMMON_UNEXPECTED_EXCEPTION,
                                            "",
                                            "",
                                            "Operation '" + op.getOperationId()
                                                    + "': Could not get a meaningful name for request body param from MethodMeta (actual name was '"
                                                    + (actualParamName != null ? actualParamName : "null")
                                                    + "'). Using default '" + inferredBodyParamName
                                                    + "'. Ensure '-parameters' compiler flag is enabled.");
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
                            props.put(
                                    inferredBodyParamName,
                                    convertOpenApiSchemaToMcpMap(bodySchema, inferredBodyParamName));
                        }
                    } else {
                        logger.warn(
                                LoggerCodeConstants.COMMON_UNEXPECTED_EXCEPTION,
                                "",
                                "",
                                "Operation '" + op.getOperationId()
                                        + "': MethodMeta not available for request body parameter name inference. Using default name 'requestBodyPayload' for schema type '"
                                        + bodySchema.getType() + "'.");
                        props.put(
                                McpConstant.PARAM_REQUEST_BODY_PAYLOAD,
                                convertOpenApiSchemaToMcpMap(bodySchema, McpConstant.PARAM_REQUEST_BODY_PAYLOAD));
                    }
                }
            });
        }
        schema.put(McpConstant.SCHEMA_PROPERTY_PROPERTIES, props);
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
            mcpMap.put(McpConstant.SCHEMA_PROPERTY_REF, openApiSchema.getRef());
        }
        if (openApiSchema.getType() != null) {
            mcpMap.put(
                    McpConstant.SCHEMA_PROPERTY_TYPE,
                    openApiSchema.getType().toString().toLowerCase());
        }
        if (openApiSchema.getFormat() != null) {
            mcpMap.put(McpConstant.SCHEMA_PROPERTY_FORMAT, openApiSchema.getFormat());
        }

        if (openApiSchema.getDescription() != null
                && !openApiSchema.getDescription().isEmpty()) {
            mcpMap.put(McpConstant.SCHEMA_PROPERTY_DESCRIPTION, openApiSchema.getDescription());
        } else {
            String defaultParamDesc = getParamDesc(openApiSchema, propertyName);
            mcpMap.put(McpConstant.SCHEMA_PROPERTY_DESCRIPTION, defaultParamDesc);
        }

        if (openApiSchema.getEnumeration() != null
                && !openApiSchema.getEnumeration().isEmpty()) {
            mcpMap.put(McpConstant.SCHEMA_PROPERTY_ENUM, openApiSchema.getEnumeration());
        }
        if (openApiSchema.getDefaultValue() != null) {
            mcpMap.put(McpConstant.SCHEMA_PROPERTY_DEFAULT, openApiSchema.getDefaultValue());
        }

        if (Schema.Type.OBJECT.equals(openApiSchema.getType()) && openApiSchema.getProperties() != null) {
            Map<String, Object> nestedProps = new HashMap<>();
            openApiSchema
                    .getProperties()
                    .forEach((name, propSchema) ->
                            // Pass the nested property name for its default description generation
                            nestedProps.put(name, convertOpenApiSchemaToMcpMap(propSchema, name)));
            mcpMap.put(McpConstant.SCHEMA_PROPERTY_PROPERTIES, nestedProps);
        }

        if (Schema.Type.ARRAY.equals(openApiSchema.getType()) && openApiSchema.getItems() != null) {
            // For array items, the 'propertyName' context is less direct, so passing null or a generic placeholder
            mcpMap.put(
                    McpConstant.SCHEMA_PROPERTY_ITEMS,
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
            typeOrRefString = " referencing '" + componentName + "';"; // Indicates it's a reference
            if (openApiSchema.getType() != null) {
                typeOrRefString += " (which is of type '"
                        + openApiSchema.getType().toString().toLowerCase() + "')";
            }

        } else if (openApiSchema.getType() != null) {
            typeOrRefString = " of type '" + openApiSchema.getType().toString().toLowerCase() + "';";
            if (openApiSchema.getFormat() != null) {
                typeOrRefString += " with format '" + openApiSchema.getFormat() + "';";
            }
        }

        String namePrefix;
        if (propertyName != null && !propertyName.isEmpty()) {
            namePrefix = "Parameter '" + propertyName + "';";
        } else {
            namePrefix = typeOrRefString.isEmpty() ? "Parameter" : "Schema";
        }

        return namePrefix + typeOrRefString + ".";
    }

    /**
     * Check if all parameters are primitive types that should be treated individually
     */
    private boolean allParamsArePrimitive(ParameterMeta[] methodParams) {
        for (ParameterMeta param : methodParams) {
            Class<?> paramType = param.getType();
            // Check if parameter is a primitive type or wrapper type
            if (!TypeSchemaUtils.isPrimitiveOrWrapper(paramType)) {
                return false;
            }
        }
        return true;
    }

    /**
     * Get method-specific configuration from operation metadata
     */
    private McpServiceFilter.McpToolConfig getMethodConfig(Operation op, McpServiceFilter.McpToolConfig defaultConfig) {
        // Try to get method-specific configuration from operation metadata
        // This would need integration with the service filter to get method-level config
        // For now, return the default config
        return defaultConfig;
    }
}
