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

import io.modelcontextprotocol.modern.McpSchema.Prompt;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.feature.McpChangeBroadcaster;
import io.modelcontextprotocol.modern.server.feature.McpSyncPrompt;
import io.modelcontextprotocol.modern.server.feature.McpSyncPromptRepository;
import io.modelcontextprotocol.modern.server.feature.PromptsPage;
import io.modelcontextprotocol.modern.server.feature.ServerChange;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * A {@link McpSyncPromptRepository} that keeps {@link McpSyncPrompt}s in a sorted,
 * concurrent map. Prompts can be added and removed at runtime; each change is broadcast
 * as {@code notifications/prompts/list_changed} when a change broadcaster is configured.
 * Listings are paged by name with an opaque key cursor when a page size is set.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class InMemorySyncPromptRepository implements McpSyncPromptRepository {

	private final Map<String, McpSyncPrompt> prompts = new ConcurrentSkipListMap<>();

	private final @Nullable McpChangeBroadcaster changeBroadcaster;

	private final @Nullable Integer pageSize;

	private InMemorySyncPromptRepository(Builder builder) {
		Assert.isTrue(builder.pageSize == null || builder.pageSize > 0, "pageSize must be positive");
		this.changeBroadcaster = builder.changeBroadcaster;
		this.pageSize = builder.pageSize;
		builder.prompts.forEach(this::put);
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Adds a prompt and notifies listening clients.
	 * @throws IllegalArgumentException if a prompt with the same name exists
	 */
	public void add(McpSyncPrompt prompt) {
		put(prompt);
		broadcastListChanged();
	}

	/**
	 * Removes a prompt by name and notifies listening clients.
	 * @return whether a prompt was removed
	 */
	public boolean remove(String name) {
		Assert.hasText(name, "name must not be empty");
		boolean removed = this.prompts.remove(name) != null;
		if (removed) {
			broadcastListChanged();
		}
		return removed;
	}

	@Override
	public PromptsPage list(McpRequestContext ctx, @Nullable String cursor) {
		Pagination.Page<Prompt> page = Pagination.page(this.prompts.values(), prompt -> prompt.definition().name(),
				McpSyncPrompt::definition, cursor, this.pageSize);
		return new PromptsPage(page.items(), page.nextCursor(), null, null);
	}

	@Override
	public @Nullable McpSyncPrompt find(McpRequestContext ctx, String name) {
		return this.prompts.get(name);
	}

	private void put(McpSyncPrompt prompt) {
		Assert.notNull(prompt, "prompt must not be null");
		String name = prompt.definition().name();
		if (this.prompts.putIfAbsent(name, prompt) != null) {
			throw new IllegalArgumentException("Prompt with name '" + name + "' already exists");
		}
	}

	private void broadcastListChanged() {
		if (this.changeBroadcaster != null) {
			this.changeBroadcaster.broadcast(new ServerChange.PromptsListChanged());
		}
	}

	/**
	 * Builder for {@link InMemorySyncPromptRepository}.
	 */
	public static final class Builder {

		private final List<McpSyncPrompt> prompts = new ArrayList<>();

		private @Nullable McpChangeBroadcaster changeBroadcaster;

		private @Nullable Integer pageSize;

		private Builder() {
		}

		public Builder prompt(McpSyncPrompt prompt) {
			Assert.notNull(prompt, "prompt must not be null");
			this.prompts.add(prompt);
			return this;
		}

		public Builder prompts(List<McpSyncPrompt> prompts) {
			Assert.notNull(prompts, "prompts must not be null");
			this.prompts.addAll(prompts);
			return this;
		}

		/**
		 * Receives {@code prompts/list_changed} broadcasts. Unset, changes are not
		 * announced.
		 */
		public Builder changeBroadcaster(@Nullable McpChangeBroadcaster changeBroadcaster) {
			this.changeBroadcaster = changeBroadcaster;
			return this;
		}

		/**
		 * Maximum number of prompts per {@code prompts/list} page. Unset returns all
		 * prompts on one page.
		 */
		public Builder pageSize(@Nullable Integer pageSize) {
			this.pageSize = pageSize;
			return this;
		}

		public InMemorySyncPromptRepository build() {
			return new InMemorySyncPromptRepository(this);
		}

	}

}
