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
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
 * from a {@link McpToolboxClient} instance alongside optional pre-configured {@link Tool}
 * beans.
 *
 * @author Stenal P Jolly
 * @since 2.1.0
 */
public class McpToolboxToolCallbackProvider implements ToolCallbackProvider {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofSeconds(30);

	private static final ObjectMapper DEFAULT_OBJECT_MAPPER = new ObjectMapper();

	private final McpToolboxClient client;

	private final Set<String> toolsets;

	private final Set<String> tools;

	private final List<Tool> preconfiguredTools;

	private final Duration timeout;

	private final ObjectMapper objectMapper;

	private final Object cacheLock = new Object();

	private final AtomicReference<ToolCallback @Nullable []> cachedCallbacks = new AtomicReference<>();

	public McpToolboxToolCallbackProvider(McpToolboxClient client) {
		this(client, List.of(), List.of(), List.of(), DEFAULT_TIMEOUT, DEFAULT_OBJECT_MAPPER);
	}

	public McpToolboxToolCallbackProvider(McpToolboxClient client, List<String> toolsets, List<String> tools) {
		this(client, toolsets, tools, List.of(), DEFAULT_TIMEOUT, DEFAULT_OBJECT_MAPPER);
	}

	public McpToolboxToolCallbackProvider(McpToolboxClient client, List<String> toolsets, List<String> tools,
			Duration timeout, ObjectMapper objectMapper) {
		this(client, toolsets, tools, List.of(), timeout, objectMapper);
	}

	public McpToolboxToolCallbackProvider(McpToolboxClient client, List<String> toolsets, List<String> tools,
			List<Tool> preconfiguredTools, Duration timeout, ObjectMapper objectMapper) {
		this(client, toolsets != null ? Collections.unmodifiableSet(new LinkedHashSet<>(toolsets)) : Set.of(),
				tools != null ? Collections.unmodifiableSet(new LinkedHashSet<>(tools)) : Set.of(), preconfiguredTools,
				timeout, objectMapper);
	}

	private McpToolboxToolCallbackProvider(McpToolboxClient client, Set<String> toolsets, Set<String> tools,
			List<Tool> preconfiguredTools, Duration timeout, ObjectMapper objectMapper) {
		Assert.notNull(client, "McpToolboxClient must not be null");
		Assert.notNull(timeout, "Timeout must not be null");
		Assert.isTrue(!timeout.isNegative() && !timeout.isZero(), "Timeout must be positive");
		Assert.notNull(objectMapper, "ObjectMapper must not be null");
		this.client = client;
		this.toolsets = toolsets;
		this.tools = tools;
		this.preconfiguredTools = preconfiguredTools != null ? List.copyOf(preconfiguredTools) : List.of();
		this.timeout = timeout;
		this.objectMapper = objectMapper;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public ToolCallback[] getToolCallbacks() {
		ToolCallback[] existing = this.cachedCallbacks.get();
		if (existing != null) {
			return existing.clone();
		}
		synchronized (this.cacheLock) {
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
		synchronized (this.cacheLock) {
			this.cachedCallbacks.set(null);
		}
	}

	private ToolCallback[] resolveToolCallbacks() {
		Map<String, ToolCallback> callbacksByName = new LinkedHashMap<>();

		try {
			if (CollectionUtils.isEmpty(this.toolsets) && CollectionUtils.isEmpty(this.tools)
					&& CollectionUtils.isEmpty(this.preconfiguredTools)) {
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

			if (!allFutures.isEmpty()) {
				CompletableFuture.allOf(allFutures.toArray(new CompletableFuture<?>[0]))
					.get(this.timeout.toMillis(), TimeUnit.MILLISECONDS);
			}

			for (CompletableFuture<Map<String, ToolDefinition>> future : toolsetFutures) {
				Map<String, ToolDefinition> definitions = future.get();
				definitions.forEach((name, def) -> callbacksByName.put(name,
						new McpToolboxToolCallback(name, def, this.client, this.timeout, this.objectMapper)));
			}

			for (CompletableFuture<Tool> future : toolFutures) {
				Tool loadedTool = future.get();
				callbacksByName.put(loadedTool.name(),
						McpToolboxToolCallback.builder()
							.tool(loadedTool)
							.timeout(this.timeout)
							.objectMapper(this.objectMapper)
							.build());
			}

			for (Tool preconfiguredTool : this.preconfiguredTools) {
				callbacksByName.put(preconfiguredTool.name(),
						McpToolboxToolCallback.builder()
							.tool(preconfiguredTool)
							.timeout(this.timeout)
							.objectMapper(this.objectMapper)
							.build());
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

	/**
	 * Fluent builder for {@link McpToolboxToolCallbackProvider}.
	 */
	public static final class Builder {

		@Nullable private McpToolboxClient client;

		private Set<String> toolsets = Set.of();

		private Set<String> tools = Set.of();

		private List<Tool> preconfiguredTools = List.of();

		private Duration timeout = DEFAULT_TIMEOUT;

		private ObjectMapper objectMapper = DEFAULT_OBJECT_MAPPER;

		private Builder() {
		}

		public Builder client(McpToolboxClient client) {
			this.client = client;
			return this;
		}

		public Builder toolsets(List<String> toolsets) {
			this.toolsets = toolsets != null ? Collections.unmodifiableSet(new LinkedHashSet<>(toolsets)) : Set.of();
			return this;
		}

		public Builder tools(List<String> tools) {
			this.tools = tools != null ? Collections.unmodifiableSet(new LinkedHashSet<>(tools)) : Set.of();
			return this;
		}

		public Builder preconfiguredTools(List<Tool> preconfiguredTools) {
			this.preconfiguredTools = preconfiguredTools != null ? List.copyOf(preconfiguredTools) : List.of();
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

		public McpToolboxToolCallbackProvider build() {
			Assert.notNull(this.client, "McpToolboxClient must not be null");
			return new McpToolboxToolCallbackProvider(this.client, this.toolsets, this.tools, this.preconfiguredTools,
					this.timeout, this.objectMapper);
		}

	}

}
