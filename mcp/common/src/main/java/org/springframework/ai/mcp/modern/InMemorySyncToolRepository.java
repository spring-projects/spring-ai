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

package org.springframework.ai.mcp.modern;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentSkipListMap;

import io.modelcontextprotocol.modern.McpSchema.Tool;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.feature.McpChangeBroadcaster;
import io.modelcontextprotocol.modern.server.feature.McpSyncTool;
import io.modelcontextprotocol.modern.server.feature.McpSyncToolRepository;
import io.modelcontextprotocol.modern.server.feature.ServerChange;
import io.modelcontextprotocol.modern.server.feature.ToolsPage;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * A {@link McpSyncToolRepository} that keeps {@link McpSyncTool}s in a sorted, concurrent
 * map. Tools can be added and removed at runtime; each change is broadcast as
 * {@code notifications/tools/list_changed} when a change broadcaster is configured.
 * Listings are paged by name with an opaque key cursor when a page size is set.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class InMemorySyncToolRepository implements McpSyncToolRepository {

	private final Map<String, McpSyncTool> tools = new ConcurrentSkipListMap<>();

	private final @Nullable McpChangeBroadcaster changeBroadcaster;

	private final @Nullable Integer pageSize;

	private InMemorySyncToolRepository(Builder builder) {
		Assert.isTrue(builder.pageSize == null || builder.pageSize > 0, "pageSize must be positive");
		this.changeBroadcaster = builder.changeBroadcaster;
		this.pageSize = builder.pageSize;
		builder.tools.forEach(this::put);
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Adds a tool and notifies listening clients.
	 * @throws IllegalArgumentException if a tool with the same name exists
	 */
	public void add(McpSyncTool tool) {
		put(tool);
		broadcastListChanged();
	}

	/**
	 * Removes a tool by name and notifies listening clients.
	 * @return whether a tool was removed
	 */
	public boolean remove(String name) {
		Assert.hasText(name, "name must not be empty");
		boolean removed = this.tools.remove(name) != null;
		if (removed) {
			broadcastListChanged();
		}
		return removed;
	}

	@Override
	public ToolsPage list(McpRequestContext ctx, @Nullable String cursor) {
		Pagination.Page<Tool> page = Pagination.page(this.tools.values(), tool -> tool.definition().name(),
				McpSyncTool::definition, cursor, this.pageSize);
		return new ToolsPage(page.items(), page.nextCursor(), null, null);
	}

	@Override
	public @Nullable McpSyncTool find(McpRequestContext ctx, String name) {
		return this.tools.get(name);
	}

	private void put(McpSyncTool tool) {
		Assert.notNull(tool, "tool must not be null");
		String name = tool.definition().name();
		if (this.tools.putIfAbsent(name, tool) != null) {
			throw new IllegalArgumentException("Tool with name '" + name + "' already exists");
		}
	}

	private void broadcastListChanged() {
		if (this.changeBroadcaster != null) {
			this.changeBroadcaster.broadcast(new ServerChange.ToolsListChanged());
		}
	}

	/**
	 * Builder for {@link InMemorySyncToolRepository}.
	 */
	public static final class Builder {

		private final List<McpSyncTool> tools = new ArrayList<>();

		private @Nullable McpChangeBroadcaster changeBroadcaster;

		private @Nullable Integer pageSize;

		private Builder() {
		}

		public Builder tool(McpSyncTool tool) {
			Assert.notNull(tool, "tool must not be null");
			this.tools.add(tool);
			return this;
		}

		public Builder tools(List<McpSyncTool> tools) {
			Assert.notNull(tools, "tools must not be null");
			this.tools.addAll(tools);
			return this;
		}

		/**
		 * Receives {@code tools/list_changed} broadcasts. Unset, changes are not
		 * announced.
		 */
		public Builder changeBroadcaster(@Nullable McpChangeBroadcaster changeBroadcaster) {
			this.changeBroadcaster = changeBroadcaster;
			return this;
		}

		/**
		 * Maximum number of tools per {@code tools/list} page. Unset returns all tools on
		 * one page.
		 */
		public Builder pageSize(@Nullable Integer pageSize) {
			this.pageSize = pageSize;
			return this;
		}

		public InMemorySyncToolRepository build() {
			return new InMemorySyncToolRepository(this);
		}

	}

}
