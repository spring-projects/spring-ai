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
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * An {@link AbstractSyncPromptRepository} that keeps {@link SyncPromptSpecification}s in
 * a sorted, concurrent map. Prompts can be added and removed at runtime; each change is
 * broadcast as {@code notifications/prompts/list_changed} when a change broadcaster is
 * configured. Every caller sees every prompt unless a {@link McpVisibilityFilter} is set.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
public final class InMemorySyncPromptRepository extends AbstractSyncPromptRepository {

	private final Map<String, SyncPromptSpecification> prompts = new ConcurrentSkipListMap<>();

	private InMemorySyncPromptRepository(Builder builder) {
		super(builder.changeBroadcaster, builder.pageSize, builder.visibility);
		builder.prompts.forEach(this::put);
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Adds a prompt and notifies listening clients.
	 * @throws IllegalArgumentException if a prompt with the same name exists
	 */
	public void add(SyncPromptSpecification specification) {
		put(specification);
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

	/**
	 * All prompt definitions, sorted by name.
	 */
	public List<Prompt> prompts() {
		List<Prompt> result = new ArrayList<>(this.prompts.size());
		this.prompts.values().forEach(spec -> result.add(spec.prompt()));
		return List.copyOf(result);
	}

	@Override
	protected @Nullable SyncPromptSpecification lookup(McpRequestContext ctx, String name) {
		return this.prompts.get(name);
	}

	@Override
	protected List<Prompt> definitions(McpRequestContext ctx) {
		return prompts();
	}

	private void put(SyncPromptSpecification specification) {
		Assert.notNull(specification, "specification must not be null");
		String name = specification.prompt().name();
		if (this.prompts.putIfAbsent(name, specification) != null) {
			throw new IllegalArgumentException("Prompt with name '" + name + "' already exists");
		}
	}

	/**
	 * Builder for {@link InMemorySyncPromptRepository}.
	 */
	public static final class Builder {

		private final List<SyncPromptSpecification> prompts = new ArrayList<>();

		private @Nullable McpChangeBroadcaster changeBroadcaster;

		private @Nullable Integer pageSize;

		private @Nullable McpVisibilityFilter visibility;

		private Builder() {
		}

		public Builder prompt(SyncPromptSpecification specification) {
			Assert.notNull(specification, "specification must not be null");
			this.prompts.add(specification);
			return this;
		}

		public Builder prompts(List<SyncPromptSpecification> specifications) {
			Assert.notNull(specifications, "specifications must not be null");
			this.prompts.addAll(specifications);
			return this;
		}

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

		/**
		 * Restricts what each caller sees. Unset, every caller sees every prompt.
		 */
		public Builder visibility(@Nullable McpVisibilityFilter visibility) {
			this.visibility = visibility;
			return this;
		}

		public InMemorySyncPromptRepository build() {
			return new InMemorySyncPromptRepository(this);
		}

	}

}
