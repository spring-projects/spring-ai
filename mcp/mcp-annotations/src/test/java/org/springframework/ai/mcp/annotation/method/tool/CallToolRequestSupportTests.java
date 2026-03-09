/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.mcp.annotation.method.tool;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;

import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema.CallToolRequest;
import io.modelcontextprotocol.spec.McpSchema.CallToolResult;
import io.modelcontextprotocol.spec.McpSchema.TextContent;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Mono;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.mcp.annotation.McpMeta;
import org.springframework.ai.mcp.annotation.McpProgressToken;
import org.springframework.ai.mcp.annotation.McpTool;
import org.springframework.ai.mcp.annotation.McpToolParam;
import org.springframework.ai.mcp.annotation.method.tool.utils.McpJsonSchemaGenerator;
import org.springframework.ai.mcp.annotation.provider.tool.SyncMcpToolProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/**
 * Tests for CallToolRequest parameter support in MCP tools.
 *
 * @author Christian Tzolov
 */
public class CallToolRequestSupportTests {

	private static final JsonMapper objectMapper = new JsonMapper();

	@Test
	public void testDynamicToolWithCallToolRequest() throws Exception {
		CallToolRequestTestProvider provider = new CallToolRequestTestProvider();
		Method method = CallToolRequestTestProvider.class.getMethod("dynamicTool", CallToolRequest.class);
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("dynamic-tool", Map.of("action", "analyze", "data", "test-data"));

		CallToolResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(result.content().get(0)).isInstanceOf(TextContent.class);
		assertThat(((TextContent) result.content().get(0)).text())
			.isEqualTo("Processed action: analyze for tool: dynamic-tool");
	}

	@Test
	public void testDynamicToolMissingRequiredParameter() throws Exception {
		CallToolRequestTestProvider provider = new CallToolRequestTestProvider();
		Method method = CallToolRequestTestProvider.class.getMethod("dynamicTool", CallToolRequest.class);
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("dynamic-tool", Map.of("data", "test-data")); // Missing
																									// 'action'
																									// parameter

		CallToolResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isTrue();
		assertThat(result.content()).hasSize(1);
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Missing required 'action' parameter");
	}

	@Test
	public void testContextAwareToolWithCallToolRequestAndExchange() throws Exception {
		CallToolRequestTestProvider provider = new CallToolRequestTestProvider();
		Method method = CallToolRequestTestProvider.class.getMethod("contextAwareTool", McpSyncServerExchange.class,
				CallToolRequest.class);
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);

		CallToolRequest request = new CallToolRequest("context-aware-tool", Map.of("key1", "value1", "key2", "value2"));

		CallToolResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Exchange available: true, Args: 2");
	}

	@Test
	public void testMixedParametersTool() throws Exception {
		CallToolRequestTestProvider provider = new CallToolRequestTestProvider();
		Method method = CallToolRequestTestProvider.class.getMethod("mixedParamsTool", CallToolRequest.class,
				String.class, Integer.class);
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("mixed-params-tool",
				Map.of("requiredParam", "test-value", "optionalParam", 42, "extraParam", "extra"));

		CallToolResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(((TextContent) result.content().get(0)).text())
			.isEqualTo("Required: test-value, Optional: 42, Total args: 3, Tool: mixed-params-tool");
	}

	@Test
	public void testMixedParametersToolWithNullOptional() throws Exception {
		CallToolRequestTestProvider provider = new CallToolRequestTestProvider();
		Method method = CallToolRequestTestProvider.class.getMethod("mixedParamsTool", CallToolRequest.class,
				String.class, Integer.class);
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("mixed-params-tool", Map.of("requiredParam", "test-value"));

		CallToolResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.content()).hasSize(1);
		assertThat(((TextContent) result.content().get(0)).text())
			.isEqualTo("Required: test-value, Optional: 0, Total args: 1, Tool: mixed-params-tool");
	}

	@Test
	public void testSchemaValidatorTool() throws Exception {
		CallToolRequestTestProvider provider = new CallToolRequestTestProvider();
		Method method = CallToolRequestTestProvider.class.getMethod("validateSchema", CallToolRequest.class);
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);

		// Test with valid schema
		CallToolRequest validRequest = new CallToolRequest("schema-validator",
				Map.of("data", "test-data", "format", "json"));

		CallToolResult validResult = callback.apply(exchange, validRequest);
		assertThat(validResult.isError()).isFalse();
		assertThat(((TextContent) validResult.content().get(0)).text())
			.isEqualTo("Schema validation successful for: schema-validator");

		// Test with invalid schema
		CallToolRequest invalidRequest = new CallToolRequest("schema-validator", Map.of("data", "test-data")); // Missing
																												// 'format'

		CallToolResult invalidResult = callback.apply(exchange, invalidRequest);
		assertThat(invalidResult.isError()).isTrue();
		assertThat(((TextContent) invalidResult.content().get(0)).text()).contains("Schema validation failed");
	}

	@Test
	public void testJsonSchemaGenerationForCallToolRequest() throws Exception {
		// Test that schema generation handles CallToolRequest properly
		Method dynamicMethod = CallToolRequestTestProvider.class.getMethod("dynamicTool", CallToolRequest.class);
		String dynamicSchema = McpJsonSchemaGenerator.generateForMethodInput(dynamicMethod);

		// Parse the schema
		JsonNode schemaNode = objectMapper.readTree(dynamicSchema);

		// Should have minimal schema with empty properties
		assertThat(schemaNode.has("type")).isTrue();
		assertThat(schemaNode.get("type").asText()).isEqualTo("object");
		assertThat(schemaNode.has("properties")).isTrue();
		assertThat(schemaNode.get("properties").size()).isEqualTo(0);
		assertThat(schemaNode.has("required")).isTrue();
		assertThat(schemaNode.get("required").size()).isEqualTo(0);
	}

	@Test
	public void testJsonSchemaGenerationForMixedParameters() throws Exception {
		// Test schema generation for method with CallToolRequest and other parameters
		Method mixedMethod = CallToolRequestTestProvider.class.getMethod("mixedParamsTool", CallToolRequest.class,
				String.class, Integer.class);
		String mixedSchema = McpJsonSchemaGenerator.generateForMethodInput(mixedMethod);

		// Parse the schema
		JsonNode schemaNode = objectMapper.readTree(mixedSchema);

		// Should have schema for non-CallToolRequest parameters only
		assertThat(schemaNode.has("properties")).isTrue();
		JsonNode properties = schemaNode.get("properties");
		assertThat(properties.has("requiredParam")).isTrue();
		assertThat(properties.has("optionalParam")).isTrue();
		assertThat(properties.size()).isEqualTo(2); // Only the regular parameters

		// Check required array
		assertThat(schemaNode.has("required")).isTrue();
		JsonNode required = schemaNode.get("required");
		assertThat(required.size()).isEqualTo(1);
		assertThat(required.get(0).asText()).isEqualTo("requiredParam");
	}

	@Test
	public void testJsonSchemaGenerationForRegularTool() throws Exception {
		// Test that regular tools still work as before
		Method regularMethod = CallToolRequestTestProvider.class.getMethod("regularTool", String.class, int.class);
		String regularSchema = McpJsonSchemaGenerator.generateForMethodInput(regularMethod);

		// Parse the schema
		JsonNode schemaNode = objectMapper.readTree(regularSchema);

		// Should have normal schema with all parameters
		assertThat(schemaNode.has("properties")).isTrue();
		JsonNode properties = schemaNode.get("properties");
		assertThat(properties.has("input")).isTrue();
		assertThat(properties.has("number")).isTrue();
		assertThat(properties.size()).isEqualTo(2);
	}

	@Test
	public void testHasCallToolRequestParameter() throws Exception {
		// Test the utility method
		Method dynamicMethod = CallToolRequestTestProvider.class.getMethod("dynamicTool", CallToolRequest.class);
		assertThat(McpJsonSchemaGenerator.hasCallToolRequestParameter(dynamicMethod)).isTrue();

		Method regularMethod = CallToolRequestTestProvider.class.getMethod("regularTool", String.class, int.class);
		assertThat(McpJsonSchemaGenerator.hasCallToolRequestParameter(regularMethod)).isFalse();

		Method mixedMethod = CallToolRequestTestProvider.class.getMethod("mixedParamsTool", CallToolRequest.class,
				String.class, Integer.class);
		assertThat(McpJsonSchemaGenerator.hasCallToolRequestParameter(mixedMethod)).isTrue();
	}

	@Test
	public void testSyncMcpToolProviderWithCallToolRequest() {
		// Test that SyncMcpToolProvider handles CallToolRequest tools correctly
		CallToolRequestTestProvider provider = new CallToolRequestTestProvider();
		SyncMcpToolProvider toolProvider = new SyncMcpToolProvider(List.of(provider));

		var toolSpecs = toolProvider.getToolSpecifications();

		// Should have all tools registered
		assertThat(toolSpecs).hasSize(9); // All 9 tools from the provider

		// Find the dynamic tool
		var dynamicToolSpec = toolSpecs.stream()
			.filter(spec -> spec.tool().name().equals("dynamic-tool"))
			.findFirst()
			.orElse(null);

		assertThat(dynamicToolSpec).isNotNull();
		assertThat(dynamicToolSpec.tool().description()).isEqualTo("Fully dynamic tool");

		// The input schema should be minimal
		var inputSchema = dynamicToolSpec.tool().inputSchema();
		assertThat(inputSchema).isNotNull();
		// Convert to string if it's a JsonSchema object
		String schemaStr = inputSchema.toString();
		assertThat(schemaStr).isNotNull();

		// Find the mixed params tool
		var mixedToolSpec = toolSpecs.stream()
			.filter(spec -> spec.tool().name().equals("mixed-params-tool"))
			.findFirst()
			.orElse(null);

		assertThat(mixedToolSpec).isNotNull();
		// The input schema should contain only the regular parameters
		var mixedSchema = mixedToolSpec.tool().inputSchema();
		assertThat(mixedSchema).isNotNull();
		// Convert to string if it's a JsonSchema object
		String mixedSchemaStr = mixedSchema.toString();
		assertThat(mixedSchemaStr).contains("requiredParam");
		assertThat(mixedSchemaStr).contains("optionalParam");
	}

	@Test
	public void testStructuredOutputWithCallToolRequest() throws Exception {
		CallToolRequestTestProvider provider = new CallToolRequestTestProvider();
		Method method = CallToolRequestTestProvider.class.getMethod("structuredOutputTool", CallToolRequest.class);
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.STRUCTURED, method, provider);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("structured-output-tool", Map.of("input", "test-message"));

		CallToolResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(result.structuredContent()).isNotNull();
		assertThat((Map<String, Object>) result.structuredContent()).containsEntry("message", "test-message");
		assertThat((Map<String, Object>) result.structuredContent()).containsEntry("value", 42);
	}

	@Test
	public void testCallToolRequestParameterInjection() throws Exception {
		// Test that CallToolRequest is properly injected as a parameter
		CallToolRequestTestProvider provider = new CallToolRequestTestProvider();
		Method method = CallToolRequestTestProvider.class.getMethod("dynamicTool", CallToolRequest.class);
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);
		CallToolRequest request = new CallToolRequest("dynamic-tool", Map.of("action", "test", "data", "sample"));

		// The callback should properly inject the CallToolRequest
		CallToolResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		// The tool should have access to the full request including the tool name
		assertThat(((TextContent) result.content().get(0)).text()).contains("tool: dynamic-tool");
	}

	@Test
	public void testProgressTokenParameterInjection() throws Exception {
		// Test that @McpProgressToken parameter receives the progress token from request
		CallToolRequestTestProvider provider = new CallToolRequestTestProvider();
		Method method = CallToolRequestTestProvider.class.getMethod("progressTokenTool", String.class, String.class);
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);

		// Create request with progress token
		CallToolRequest request = CallToolRequest.builder()
			.name("progress-token-tool")
			.arguments(Map.of("input", "test-input"))
			.progressToken("test-progress-token-123")
			.build();

		CallToolResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(((TextContent) result.content().get(0)).text())
			.isEqualTo("Input: test-input, Progress Token: test-progress-token-123");
	}

	@Test
	public void testProgressTokenParameterWithNullToken() throws Exception {
		// Test that @McpProgressToken parameter handles null progress token
		CallToolRequestTestProvider provider = new CallToolRequestTestProvider();
		Method method = CallToolRequestTestProvider.class.getMethod("progressTokenTool", String.class, String.class);
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);

		// Create request without progress token
		CallToolRequest request = new CallToolRequest("progress-token-tool", Map.of("input", "test-input"));

		CallToolResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Input: test-input, Progress Token: null");
	}

	@Test
	public void testMixedSpecialParameters() throws Exception {
		// Test tool with all types of special parameters
		CallToolRequestTestProvider provider = new CallToolRequestTestProvider();
		Method method = CallToolRequestTestProvider.class.getMethod("mixedSpecialParamsTool",
				McpSyncServerExchange.class, CallToolRequest.class, String.class, String.class);
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);

		CallToolRequest request = CallToolRequest.builder()
			.name("mixed-special-params-tool")
			.arguments(Map.of("regularParam", "test-value"))
			.progressToken("progress-123")
			.build();

		CallToolResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(((TextContent) result.content().get(0)).text())
			.isEqualTo("Exchange: present, Request: mixed-special-params-tool, Token: progress-123, Param: test-value");
	}

	@Test
	public void testJsonSchemaGenerationExcludesProgressToken() throws Exception {
		// Test that schema generation excludes @McpProgressToken parameters
		Method progressTokenMethod = CallToolRequestTestProvider.class.getMethod("progressTokenTool", String.class,
				String.class);
		String progressTokenSchema = McpJsonSchemaGenerator.generateForMethodInput(progressTokenMethod);

		// Parse the schema
		JsonNode schemaNode = objectMapper.readTree(progressTokenSchema);

		// Should only have the 'input' parameter, not the progressToken
		assertThat(schemaNode.has("properties")).isTrue();
		JsonNode properties = schemaNode.get("properties");
		assertThat(properties.has("input")).isTrue();
		assertThat(properties.has("progressToken")).isFalse();
		assertThat(properties.size()).isEqualTo(1);

		// Check required array
		assertThat(schemaNode.has("required")).isTrue();
		JsonNode required = schemaNode.get("required");
		assertThat(required.size()).isEqualTo(1);
		assertThat(required.get(0).asText()).isEqualTo("input");
	}

	@Test
	public void testJsonSchemaGenerationForMixedSpecialParameters() throws Exception {
		// Test schema generation for method with all special parameters
		Method mixedMethod = CallToolRequestTestProvider.class.getMethod("mixedSpecialParamsTool",
				McpSyncServerExchange.class, CallToolRequest.class, String.class, String.class);
		String mixedSchema = McpJsonSchemaGenerator.generateForMethodInput(mixedMethod);

		// Parse the schema
		JsonNode schemaNode = objectMapper.readTree(mixedSchema);

		// Should only have the 'regularParam' parameter
		assertThat(schemaNode.has("properties")).isTrue();
		JsonNode properties = schemaNode.get("properties");
		assertThat(properties.has("regularParam")).isTrue();
		assertThat(properties.has("progressToken")).isFalse();
		assertThat(properties.size()).isEqualTo(1);

		// Check required array
		assertThat(schemaNode.has("required")).isTrue();
		JsonNode required = schemaNode.get("required");
		assertThat(required.size()).isEqualTo(1);
		assertThat(required.get(0).asText()).isEqualTo("regularParam");
	}

	@Test
	public void testSyncMcpToolProviderWithProgressToken() {
		// Test that SyncMcpToolProvider handles @McpProgressToken tools correctly
		CallToolRequestTestProvider provider = new CallToolRequestTestProvider();
		SyncMcpToolProvider toolProvider = new SyncMcpToolProvider(List.of(provider));

		var toolSpecs = toolProvider.getToolSpecifications();

		// Find the progress token tool
		var progressTokenToolSpec = toolSpecs.stream()
			.filter(spec -> spec.tool().name().equals("progress-token-tool"))
			.findFirst()
			.orElse(null);

		assertThat(progressTokenToolSpec).isNotNull();
		assertThat(progressTokenToolSpec.tool().description()).isEqualTo("Tool with progress token");

		// The input schema should only contain the regular parameter
		var inputSchema = progressTokenToolSpec.tool().inputSchema();
		assertThat(inputSchema).isNotNull();
		String schemaStr = inputSchema.toString();
		assertThat(schemaStr).contains("input");
		assertThat(schemaStr).doesNotContain("progressToken");
	}

	@Test
	public void testMetaParameterInjection() throws Exception {
		// Test that McpMeta parameter receives the meta from request
		CallToolRequestTestProvider provider = new CallToolRequestTestProvider();
		Method method = CallToolRequestTestProvider.class.getMethod("metaTool", String.class, McpMeta.class);
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);

		// Create request with meta data
		CallToolRequest request = CallToolRequest.builder()
			.name("meta-tool")
			.arguments(Map.of("input", "test-input"))
			.meta(Map.of("userId", "user123", "sessionId", "session456"))
			.build();

		CallToolResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(((TextContent) result.content().get(0)).text()).contains("Input: test-input")
			.contains("Meta: {userId=user123, sessionId=session456}");
	}

	@Test
	public void testMetaParameterWithNullMeta() throws Exception {
		// Test that McpMeta parameter handles null meta
		CallToolRequestTestProvider provider = new CallToolRequestTestProvider();
		Method method = CallToolRequestTestProvider.class.getMethod("metaTool", String.class, McpMeta.class);
		SyncMcpToolMethodCallback callback = new SyncMcpToolMethodCallback(ReturnMode.TEXT, method, provider);

		McpSyncServerExchange exchange = mock(McpSyncServerExchange.class);

		// Create request without meta
		CallToolRequest request = new CallToolRequest("meta-tool", Map.of("input", "test-input"));

		CallToolResult result = callback.apply(exchange, request);

		assertThat(result).isNotNull();
		assertThat(result.isError()).isFalse();
		assertThat(((TextContent) result.content().get(0)).text()).isEqualTo("Input: test-input, Meta: {}");
	}

	@Test
	public void testJsonSchemaGenerationExcludesMeta() throws Exception {
		// Test that schema generation excludes McpMeta parameters
		Method metaMethod = CallToolRequestTestProvider.class.getMethod("metaTool", String.class, McpMeta.class);
		String metaSchema = McpJsonSchemaGenerator.generateForMethodInput(metaMethod);

		// Parse the schema
		JsonNode schemaNode = objectMapper.readTree(metaSchema);

		// Should only have the 'input' parameter, not the meta
		assertThat(schemaNode.has("properties")).isTrue();
		JsonNode properties = schemaNode.get("properties");
		assertThat(properties.has("input")).isTrue();
		assertThat(properties.has("meta")).isFalse();
		assertThat(properties.size()).isEqualTo(1);

		// Check required array
		assertThat(schemaNode.has("required")).isTrue();
		JsonNode required = schemaNode.get("required");
		assertThat(required.size()).isEqualTo(1);
		assertThat(required.get(0).asText()).isEqualTo("input");
	}

	private static class CallToolRequestTestProvider {

		/**
		 * Tool that only takes CallToolRequest - for fully dynamic handling
		 */
		@McpTool(name = "dynamic-tool", description = "Fully dynamic tool")
		public CallToolResult dynamicTool(CallToolRequest request) {
			// Access full request details
			String toolName = request.name();
			Map<String, Object> arguments = request.arguments();

			// Custom validation
			if (!arguments.containsKey("action")) {
				return CallToolResult.builder()
					.isError(true)
					.addTextContent("Missing required 'action' parameter")
					.build();
			}

			String action = (String) arguments.get("action");
			return CallToolResult.builder()
				.addTextContent("Processed action: " + action + " for tool: " + toolName)
				.build();
		}

		/**
		 * Tool with CallToolRequest and Exchange parameters
		 */
		@McpTool(name = "context-aware-tool", description = "Tool with context and request")
		public CallToolResult contextAwareTool(McpSyncServerExchange exchange, CallToolRequest request) {
			// Exchange is available for context
			Map<String, Object> arguments = request.arguments();

			return CallToolResult.builder()
				.addTextContent("Exchange available: " + (exchange != null) + ", Args: " + arguments.size())
				.build();
		}

		/**
		 * Tool with mixed parameters - CallToolRequest plus regular parameters
		 */
		@McpTool(name = "mixed-params-tool", description = "Tool with mixed parameters")
		public CallToolResult mixedParamsTool(CallToolRequest request,
				@McpToolParam(description = "Required string parameter", required = true) String requiredParam,
				@McpToolParam(description = "Optional integer parameter", required = false) Integer optionalParam) {

			Map<String, Object> allArguments = request.arguments();

			return CallToolResult.builder()
				.addTextContent(String.format("Required: %s, Optional: %d, Total args: %d, Tool: %s", requiredParam,
						optionalParam != null ? optionalParam : 0, allArguments.size(), request.name()))
				.build();
		}

		/**
		 * Tool that validates custom schema from CallToolRequest
		 */
		@McpTool(name = "schema-validator", description = "Validates against custom schema")
		public CallToolResult validateSchema(CallToolRequest request) {
			Map<String, Object> arguments = request.arguments();

			// Custom schema validation logic
			boolean hasRequiredFields = arguments.containsKey("data") && arguments.containsKey("format");

			if (!hasRequiredFields) {
				return CallToolResult.builder()
					.isError(true)
					.addTextContent("Schema validation failed: missing required fields 'data' and 'format'")
					.build();
			}

			return CallToolResult.builder()
				.addTextContent("Schema validation successful for: " + request.name())
				.build();
		}

		/**
		 * Tool with @McpProgressToken parameter
		 */
		@McpTool(name = "progress-token-tool", description = "Tool with progress token")
		public CallToolResult progressTokenTool(
				@McpToolParam(description = "Input parameter", required = true) String input,
				@McpProgressToken String progressToken) {
			return CallToolResult.builder()
				.addTextContent("Input: " + input + ", Progress Token: " + progressToken)
				.build();
		}

		/**
		 * Tool with mixed special parameters including @McpProgressToken
		 */
		@McpTool(name = "mixed-special-params-tool", description = "Tool with all special parameters")
		public CallToolResult mixedSpecialParamsTool(McpSyncServerExchange exchange, CallToolRequest request,
				@McpProgressToken String progressToken,
				@McpToolParam(description = "Regular parameter", required = true) String regularParam) {

			return CallToolResult.builder()
				.addTextContent(String.format("Exchange: %s, Request: %s, Token: %s, Param: %s",
						exchange != null ? "present" : "null", request != null ? request.name() : "null",
						progressToken != null ? progressToken : "null", regularParam))
				.build();
		}

		/**
		 * Tool with McpMeta parameter
		 */
		@McpTool(name = "meta-tool", description = "Tool with meta parameter")
		public CallToolResult metaTool(@McpToolParam(description = "Input parameter", required = true) String input,
				McpMeta meta) {
			String metaInfo = meta != null && meta.meta() != null ? meta.meta().toString() : "null";
			return CallToolResult.builder().addTextContent("Input: " + input + ", Meta: " + metaInfo).build();
		}

		/**
		 * Regular tool without CallToolRequest for comparison
		 */
		@McpTool(name = "regular-tool", description = "Regular tool without CallToolRequest")
		public String regularTool(String input, int number) {
			return "Regular: " + input + " - " + number;
		}

		/**
		 * Tool that returns structured output
		 */
		@McpTool(name = "structured-output-tool", description = "Tool with structured output")
		public TestResult structuredOutputTool(CallToolRequest request) {
			Map<String, Object> arguments = request.arguments();
			String input = (String) arguments.get("input");

			return new TestResult(input != null ? input : "default", 42);
		}

		/**
		 * Simple reactive tool for negative testing
		 */
		@McpTool(name = "reactive-tool", description = "Hello World Reactive Tool")
		public Mono<String> simpleReactive(CallToolRequest request) {
			return Mono.just("Hello World");
		}

	}

	public static class TestResult {

		public String message;

		public int value;

		public TestResult(String message, int value) {
			this.message = message;
			this.value = value;
		}

	}

}
