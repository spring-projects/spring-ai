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
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * An {@link AbstractSyncToolRepository} that keeps {@link SyncToolSpecification}s in a
 * sorted, concurrent map. Tools can be added and removed at runtime; each change is
 * broadcast as {@code notifications/tools/list_changed} when a change broadcaster is
 * configured. Every caller sees every tool unless a {@link McpVisibilityFilter} is set,
 * in which case hidden tools are neither listed nor callable and listings are
 * {@code cacheScope: private}.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class InMemorySyncToolRepository extends AbstractSyncToolRepository {

	private final Map<String, SyncToolSpecification> tools = new ConcurrentSkipListMap<>();

	private InMemorySyncToolRepository(Builder builder) {
		super(builder.changeBroadcaster, builder.pageSize, builder.visibility);
		builder.tools.forEach(this::put);
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Adds a tool and notifies listening clients.
	 * @throws IllegalArgumentException if a tool with the same name exists
	 */
	public void add(SyncToolSpecification specification) {
		put(specification);
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

	/**
	 * All tool definitions, sorted by name, regardless of any visibility filter.
	 */
	public List<Tool> tools() {
		List<Tool> result = new ArrayList<>(this.tools.size());
		this.tools.values().forEach(spec -> result.add(spec.tool()));
		return List.copyOf(result);
	}

	@Override
	protected @Nullable SyncToolSpecification lookup(McpRequestContext ctx, String name) {
		return this.tools.get(name);
	}

	@Override
	protected List<Tool> definitions(McpRequestContext ctx) {
		return tools();
	}

	private void put(SyncToolSpecification specification) {
		Assert.notNull(specification, "specification must not be null");
		String name = specification.tool().name();
		if (this.tools.putIfAbsent(name, specification) != null) {
			throw new IllegalArgumentException("Tool with name '" + name + "' already exists");
		}
	}

	/**
	 * Builder for {@link InMemorySyncToolRepository}.
	 */
	public static final class Builder {

		private final List<SyncToolSpecification> tools = new ArrayList<>();

		private @Nullable McpChangeBroadcaster changeBroadcaster;

		private @Nullable Integer pageSize;

		private @Nullable McpVisibilityFilter visibility;

		private Builder() {
		}

		public Builder tool(SyncToolSpecification specification) {
			Assert.notNull(specification, "specification must not be null");
			this.tools.add(specification);
			return this;
		}

		public Builder tools(List<SyncToolSpecification> specifications) {
			Assert.notNull(specifications, "specifications must not be null");
			this.tools.addAll(specifications);
			return this;
		}

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

		/**
		 * Restricts what each caller sees. Unset, every caller sees every tool.
		 */
		public Builder visibility(@Nullable McpVisibilityFilter visibility) {
			this.visibility = visibility;
			return this;
		}

		public InMemorySyncToolRepository build() {
			return new InMemorySyncToolRepository(this);
		}

	}

}
