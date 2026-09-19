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

package org.springframework.ai.mcp.toolbox;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.tool.Tool;
import com.google.cloud.mcp.tool.ToolDefinition.Parameter;
import com.google.cloud.mcp.tool.ToolResult;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.execution.ToolExecutionException;
import org.springframework.ai.tool.metadata.ToolMetadata;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Adapter bridging a Google Cloud MCP Toolbox {@link Tool} to Spring AI's
 * {@link ToolCallback} interface.
 *
 * @author Stenal P Jolly
 * @since 2.1.0
 */
public class McpToolboxToolCallback implements ToolCallback {

	public static final String TOOL_CONTEXT_HEADERS_KEY = "mcpToolboxHeaders";

	public static final String TOOL_CONTEXT_AUTH_TOKENS_KEY = "mcpToolboxAuthTokens";

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

	private static final int MAX_ERROR_MESSAGE_LENGTH = 512;

	private static final ObjectMapper DEFAULT_OBJECT_MAPPER = new ObjectMapper();

	private final Tool tool;

	private final ToolDefinition toolDefinition;

	private final ToolMetadata toolMetadata;

	private final Duration timeout;

	private final ObjectMapper objectMapper;

	public McpToolboxToolCallback(Tool tool) {
		this(tool, ToolMetadata.builder().build(), DEFAULT_TIMEOUT, DEFAULT_OBJECT_MAPPER);
	}

	public McpToolboxToolCallback(Tool tool, Duration timeout, ObjectMapper objectMapper) {
		this(tool, ToolMetadata.builder().build(), timeout, objectMapper);
	}

	public McpToolboxToolCallback(Tool tool, ToolMetadata toolMetadata, Duration timeout, ObjectMapper objectMapper) {
		Assert.notNull(tool, "Tool must not be null");
		Assert.notNull(toolMetadata, "ToolMetadata must not be null");
		Assert.notNull(timeout, "Timeout must not be null");
		Assert.isTrue(!timeout.isNegative() && !timeout.isZero(), "Timeout must be positive");
		Assert.notNull(objectMapper, "ObjectMapper must not be null");
		this.tool = tool;
		this.toolMetadata = toolMetadata;
		this.timeout = timeout;
		this.objectMapper = objectMapper;
		this.toolDefinition = buildSpringAiToolDefinition(tool, objectMapper);
	}

	public McpToolboxToolCallback(String toolName, com.google.cloud.mcp.tool.ToolDefinition definition,
			McpToolboxClient client) {
		this(new Tool(toolName, definition, client), ToolMetadata.builder().build(), DEFAULT_TIMEOUT,
				DEFAULT_OBJECT_MAPPER);
	}

	public McpToolboxToolCallback(String toolName, com.google.cloud.mcp.tool.ToolDefinition definition,
			McpToolboxClient client, Duration timeout, ObjectMapper objectMapper) {
		this(new Tool(toolName, definition, client), ToolMetadata.builder().build(), timeout, objectMapper);
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public ToolDefinition getToolDefinition() {
		return this.toolDefinition;
	}

	@Override
	public ToolMetadata getToolMetadata() {
		return this.toolMetadata;
	}

	@Override
	public String call(String toolInput) {
		return call(toolInput, null);
	}

	@Override
	public String call(String toolInput, @Nullable ToolContext toolContext) {
		try {
			Map<String, Object> parsedArguments = new LinkedHashMap<>();
			if (StringUtils.hasText(toolInput)) {
				Map<String, Object> parsed = this.objectMapper.readValue(toolInput,
						new TypeReference<Map<String, Object>>() {
						});
				if (parsed != null) {
					parsedArguments.putAll(parsed);
				}
			}

			Map<String, String> dynamicHeaders = extractDynamicHeaders(toolContext);
			Tool executionTool = applyDynamicAuthTokens(this.tool, toolContext);

			ToolResult result = executionTool.execute(parsedArguments, dynamicHeaders)
				.get(this.timeout.toMillis(), TimeUnit.MILLISECONDS);
			if (result.isError()) {
				String sanitizedError = sanitizeErrorMessage(result.text());
				throw new ToolExecutionException(this.toolDefinition,
						new IllegalStateException("MCP Toolbox tool execution failed: " + sanitizedError));
			}
			return result.text();
		}
		catch (ToolExecutionException ex) {
			throw ex;
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new ToolExecutionException(this.toolDefinition, ex);
		}
		catch (ExecutionException ex) {
			Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
			throw new ToolExecutionException(this.toolDefinition, cause);
		}
		catch (TimeoutException ex) {
			throw new ToolExecutionException(this.toolDefinition, new TimeoutException(
					"MCP Toolbox tool '" + this.tool.name() + "' timed out after " + this.timeout));
		}
		catch (Exception ex) {
			throw new ToolExecutionException(this.toolDefinition, ex);
		}
	}

	private static Map<String, String> extractDynamicHeaders(@Nullable ToolContext toolContext) {
		if (toolContext == null || CollectionUtils.isEmpty(toolContext.getContext())) {
			return Map.of();
		}
		Map<String, String> headers = new LinkedHashMap<>();
		Object explicitHeaders = toolContext.getContext().get(TOOL_CONTEXT_HEADERS_KEY);
		if (explicitHeaders instanceof Map<?, ?> headerMap) {
			for (Map.Entry<?, ?> entry : headerMap.entrySet()) {
				if (entry.getKey() instanceof String key && entry.getValue() instanceof String val
						&& isAllowedDynamicHeader(key)) {
					headers.put(key, val);
				}
			}
		}
		for (Map.Entry<String, Object> entry : toolContext.getContext().entrySet()) {
			String key = entry.getKey();
			if (!TOOL_CONTEXT_HEADERS_KEY.equals(key) && !TOOL_CONTEXT_AUTH_TOKENS_KEY.equals(key)
					&& entry.getValue() instanceof String val && isAllowedDynamicHeader(key)) {
				headers.putIfAbsent(key, val);
			}
		}
		return Map.copyOf(headers);
	}

	private static boolean isAllowedDynamicHeader(String headerName) {
		return StringUtils.hasText(headerName) && !"x-goog-api-key".equalsIgnoreCase(headerName.strip());
	}

	private static Tool applyDynamicAuthTokens(Tool baseTool, @Nullable ToolContext toolContext) {
		if (toolContext == null || CollectionUtils.isEmpty(toolContext.getContext())) {
			return baseTool;
		}
		Tool updatedTool = baseTool;
		Object authTokens = toolContext.getContext().get(TOOL_CONTEXT_AUTH_TOKENS_KEY);
		if (authTokens instanceof Map<?, ?> tokenMap) {
			for (Map.Entry<?, ?> entry : tokenMap.entrySet()) {
				if (entry.getKey() instanceof String serviceName && entry.getValue() instanceof String token
						&& StringUtils.hasText(serviceName) && StringUtils.hasText(token)) {
					updatedTool = updatedTool.addAuthTokenGetter(serviceName,
							() -> CompletableFuture.completedFuture(token));
				}
			}
		}
		return updatedTool;
	}

	private static String sanitizeErrorMessage(@Nullable String rawText) {
		if (!StringUtils.hasText(rawText)) {
			return "Unknown tool execution error";
		}
		String trimmed = rawText.strip();
		if (trimmed.length() > MAX_ERROR_MESSAGE_LENGTH) {
			return trimmed.substring(0, MAX_ERROR_MESSAGE_LENGTH) + "... [truncated]";
		}
		return trimmed;
	}

	private static ToolDefinition buildSpringAiToolDefinition(Tool tool, ObjectMapper mapper) {
		Map<String, Object> schema = new LinkedHashMap<>();
		schema.put("type", "object");
		Map<String, Object> properties = new LinkedHashMap<>();
		List<String> required = new ArrayList<>();

		if (tool.definition() != null && tool.definition().parameters() != null) {
			for (Parameter param : tool.definition().parameters()) {
				Map<String, Object> prop = new LinkedHashMap<>();
				prop.put("type", StringUtils.hasText(param.type()) ? param.type() : "string");
				if (StringUtils.hasText(param.description())) {
					prop.put("description", param.description());
				}
				if (param.defaultValue() != null) {
					prop.put("default", param.defaultValue());
				}
				properties.put(param.name(), prop);
				if (param.required()) {
					required.add(param.name());
				}
			}
		}
		schema.put("properties", properties);
		if (!required.isEmpty()) {
			schema.put("required", required);
		}
		schema.put("additionalProperties", false);

		try {
			String inputSchemaJson = mapper.writeValueAsString(schema);
			return ToolDefinition.builder()
				.name(tool.name())
				.description(tool.definition() != null && StringUtils.hasText(tool.definition().description())
						? tool.definition().description() : tool.name())
				.inputSchema(inputSchemaJson)
				.build();
		}
		catch (Exception ex) {
			throw new IllegalStateException("Failed to serialize JSON schema for tool " + tool.name(), ex);
		}
	}

	/**
	 * Fluent builder for {@link McpToolboxToolCallback}.
	 */
	public static final class Builder {

		@Nullable private Tool tool;

		private ToolMetadata toolMetadata = ToolMetadata.builder().build();

		private Duration timeout = DEFAULT_TIMEOUT;

		private ObjectMapper objectMapper = DEFAULT_OBJECT_MAPPER;

		private Builder() {
		}

		public Builder tool(Tool tool) {
			this.tool = tool;
			return this;
		}

		public Builder toolMetadata(ToolMetadata toolMetadata) {
			Assert.notNull(toolMetadata, "ToolMetadata must not be null");
			this.toolMetadata = toolMetadata;
			return this;
		}

		public Builder timeout(Duration timeout) {
			Assert.notNull(timeout, "Timeout must not be null");
			this.timeout = timeout;
			return this;
		}

		public Builder objectMapper(ObjectMapper objectMapper) {
			Assert.notNull(objectMapper, "ObjectMapper must not be null");
			this.objectMapper = objectMapper;
			return this;
		}

		public McpToolboxToolCallback build() {
			Assert.notNull(this.tool, "Tool must not be null");
			return new McpToolboxToolCallback(this.tool, this.toolMetadata, this.timeout, this.objectMapper);
		}

	}

}
