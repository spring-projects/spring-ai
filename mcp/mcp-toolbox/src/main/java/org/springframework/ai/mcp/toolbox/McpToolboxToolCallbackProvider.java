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
import java.util.concurrent.atomic.AtomicReference;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.cloud.mcp.McpToolboxClient;
import com.google.cloud.mcp.tool.Tool;
import com.google.cloud.mcp.tool.ToolDefinition;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * {@link ToolCallbackProvider} implementation that resolves and memoizes database tools
 * from a {@link McpToolboxClient} instance.
 *
 * @author Stenal P Jolly
 * @since 2.1.0
 */
public class McpToolboxToolCallbackProvider implements ToolCallbackProvider {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

	private static final ObjectMapper DEFAULT_OBJECT_MAPPER = new ObjectMapper();

	private final McpToolboxClient client;

	private final List<String> toolsets;

	private final List<String> tools;

	private final Duration timeout;

	private final ObjectMapper objectMapper;

	private final AtomicReference<ToolCallback @Nullable []> cachedCallbacks = new AtomicReference<>();

	public McpToolboxToolCallbackProvider(McpToolboxClient client) {
		this(client, List.of(), List.of(), DEFAULT_TIMEOUT, DEFAULT_OBJECT_MAPPER);
	}

	public McpToolboxToolCallbackProvider(McpToolboxClient client, List<String> toolsets, List<String> tools) {
		this(client, toolsets, tools, DEFAULT_TIMEOUT, DEFAULT_OBJECT_MAPPER);
	}

	public McpToolboxToolCallbackProvider(McpToolboxClient client, List<String> toolsets, List<String> tools,
			Duration timeout, ObjectMapper objectMapper) {
		Assert.notNull(client, "McpToolboxClient must not be null");
		Assert.notNull(timeout, "Timeout must not be null");
		Assert.isTrue(!timeout.isNegative() && !timeout.isZero(), "Timeout must be positive");
		Assert.notNull(objectMapper, "ObjectMapper must not be null");
		this.client = client;
		this.toolsets = toolsets != null ? List.copyOf(toolsets) : List.of();
		this.tools = tools != null ? List.copyOf(tools) : List.of();
		this.timeout = timeout;
		this.objectMapper = objectMapper;
	}

	@Override
	public ToolCallback[] getToolCallbacks() {
		ToolCallback[] existing = this.cachedCallbacks.get();
		if (existing != null) {
			return existing.clone();
		}
		synchronized (this.cachedCallbacks) {
			existing = this.cachedCallbacks.get();
			if (existing == null) {
				existing = resolveToolCallbacks();
				this.cachedCallbacks.set(existing);
			}
			return existing.clone();
		}
	}

	/**
	 * Clears the memoized tool callbacks so that the next {@link #getToolCallbacks()}
	 * invocation reloads tool definitions from the MCP Toolbox server.
	 */
	public void invalidateCache() {
		synchronized (this.cachedCallbacks) {
			this.cachedCallbacks.set(null);
		}
	}

	private ToolCallback[] resolveToolCallbacks() {
		Map<String, ToolCallback> callbacksByName = new LinkedHashMap<>();

		try {
			if (CollectionUtils.isEmpty(this.toolsets) && CollectionUtils.isEmpty(this.tools)) {
				Map<String, ToolDefinition> definitions = this.client.listTools()
					.get(this.timeout.toMillis(), TimeUnit.MILLISECONDS);
				definitions.forEach((name, def) -> callbacksByName.put(name,
						new McpToolboxToolCallback(name, def, this.client, this.timeout, this.objectMapper)));
				return callbacksByName.values().toArray(new ToolCallback[0]);
			}

			List<CompletableFuture<Map<String, ToolDefinition>>> toolsetFutures = new ArrayList<>();
			for (String toolsetName : this.toolsets) {
				toolsetFutures.add(this.client.loadToolset(toolsetName));
			}

			List<CompletableFuture<Tool>> toolFutures = new ArrayList<>();
			for (String toolName : this.tools) {
				toolFutures.add(this.client.loadTool(toolName));
			}

			List<CompletableFuture<?>> allFutures = new ArrayList<>(toolsetFutures.size() + toolFutures.size());
			allFutures.addAll(toolsetFutures);
			allFutures.addAll(toolFutures);

			CompletableFuture.allOf(allFutures.toArray(new CompletableFuture<?>[0]))
				.get(this.timeout.toMillis(), TimeUnit.MILLISECONDS);

			for (CompletableFuture<Map<String, ToolDefinition>> future : toolsetFutures) {
				Map<String, ToolDefinition> definitions = future.get();
				definitions.forEach((name, def) -> callbacksByName.put(name,
						new McpToolboxToolCallback(name, def, this.client, this.timeout, this.objectMapper)));
			}

			for (CompletableFuture<Tool> future : toolFutures) {
				Tool loadedTool = future.get();
				callbacksByName.put(loadedTool.name(),
						new McpToolboxToolCallback(loadedTool, this.timeout, this.objectMapper));
			}

			return callbacksByName.values().toArray(new ToolCallback[0]);
		}
		catch (InterruptedException ex) {
			Thread.currentThread().interrupt();
			throw new IllegalStateException("Interrupted while loading tools from MCP Toolbox server", ex);
		}
		catch (ExecutionException ex) {
			Throwable cause = ex.getCause() != null ? ex.getCause() : ex;
			throw new IllegalStateException("Failed to load tools from MCP Toolbox server", cause);
		}
		catch (TimeoutException ex) {
			throw new IllegalStateException(
					"Timed out after " + this.timeout + " while loading tools from MCP Toolbox server", ex);
		}
	}

}
