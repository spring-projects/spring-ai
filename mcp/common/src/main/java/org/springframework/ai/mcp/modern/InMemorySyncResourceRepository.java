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

import io.modelcontextprotocol.modern.McpSchema.Resource;
import io.modelcontextprotocol.modern.McpSchema.ResourceTemplate;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.feature.McpChangeBroadcaster;
import io.modelcontextprotocol.modern.server.feature.McpSyncResource;
import io.modelcontextprotocol.modern.server.feature.McpSyncResourceRepository;
import io.modelcontextprotocol.modern.server.feature.ResourceTemplatesPage;
import io.modelcontextprotocol.modern.server.feature.ResourcesPage;
import io.modelcontextprotocol.modern.server.feature.ServerChange;
import io.modelcontextprotocol.util.DefaultMcpUriTemplateManagerFactory;
import io.modelcontextprotocol.util.McpUriTemplateManager;
import io.modelcontextprotocol.util.McpUriTemplateManagerFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * A {@link McpSyncResourceRepository} that keeps {@link SyncResourceEntry}s and
 * {@link SyncResourceTemplateEntry}s in sorted, concurrent maps. A {@code resources/read}
 * URI is served by the resource registered under it, or else by the first template
 * matching it in URI-template order. Resources and templates can be added and removed at
 * runtime; each change is broadcast as {@code notifications/resources/list_changed} when
 * a change broadcaster is configured, and {@link #resourceUpdated(String)} notifies
 * subscribers of a changed resource. Listings are paged with an opaque key cursor when a
 * page size is set.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public final class InMemorySyncResourceRepository implements McpSyncResourceRepository {

	private final Map<String, SyncResourceEntry> resources = new ConcurrentSkipListMap<>();

	private final Map<String, CompiledTemplate> templates = new ConcurrentSkipListMap<>();

	private final McpUriTemplateManagerFactory uriTemplateManagerFactory;

	private final @Nullable McpChangeBroadcaster changeBroadcaster;

	private final @Nullable Integer pageSize;

	private final boolean subscribeSupported;

	private InMemorySyncResourceRepository(Builder builder) {
		Assert.isTrue(builder.pageSize == null || builder.pageSize > 0, "pageSize must be positive");
		this.uriTemplateManagerFactory = builder.uriTemplateManagerFactory;
		this.changeBroadcaster = builder.changeBroadcaster;
		this.pageSize = builder.pageSize;
		this.subscribeSupported = builder.subscribeSupported;
		builder.resources.forEach(this::put);
		builder.resourceTemplates.forEach(this::put);
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Adds a resource and notifies listening clients.
	 * @throws IllegalArgumentException if a resource with the same URI exists
	 */
	public void add(SyncResourceEntry entry) {
		put(entry);
		broadcastListChanged();
	}

	/**
	 * Adds a resource template and notifies listening clients.
	 * @throws IllegalArgumentException if a template with the same URI template exists,
	 * or the URI template is invalid
	 */
	public void add(SyncResourceTemplateEntry entry) {
		put(entry);
		broadcastListChanged();
	}

	/**
	 * Removes the resource or resource template with this URI and notifies listening
	 * clients.
	 * @return whether anything was removed
	 */
	public boolean remove(String uri) {
		Assert.hasText(uri, "uri must not be empty");
		boolean removed = this.resources.remove(uri) != null;
		removed |= this.templates.remove(uri) != null;
		if (removed) {
			broadcastListChanged();
		}
		return removed;
	}

	/**
	 * Notifies subscribers that the content of {@code uri} changed. Reaches clients only
	 * when the repository was built with {@code subscribeSupported}.
	 */
	public void resourceUpdated(String uri) {
		Assert.hasText(uri, "uri must not be empty");
		if (this.changeBroadcaster != null) {
			this.changeBroadcaster.broadcast(new ServerChange.ResourceUpdated(uri));
		}
	}

	@Override
	public ResourcesPage list(McpRequestContext ctx, @Nullable String cursor) {
		Pagination.Page<Resource> page = Pagination.page(this.resources.values(), entry -> entry.resource().uri(),
				SyncResourceEntry::resource, cursor, this.pageSize);
		return new ResourcesPage(page.items(), page.nextCursor(), null, null);
	}

	@Override
	public ResourceTemplatesPage listTemplates(McpRequestContext ctx, @Nullable String cursor) {
		Pagination.Page<ResourceTemplate> page = Pagination.page(this.templates.values(),
				template -> template.entry().resourceTemplate().uriTemplate(),
				template -> template.entry().resourceTemplate(), cursor, this.pageSize);
		return new ResourceTemplatesPage(page.items(), page.nextCursor(), null, null);
	}

	@Override
	public @Nullable McpSyncResource find(McpRequestContext ctx, String uri) {
		SyncResourceEntry resource = this.resources.get(uri);
		if (resource != null) {
			return resource.handler();
		}
		for (CompiledTemplate template : this.templates.values()) {
			if (template.matcher().matches(uri)) {
				return template.entry().handler();
			}
		}
		return null;
	}

	@Override
	public boolean supportsSubscribe() {
		return this.subscribeSupported;
	}

	private void put(SyncResourceEntry entry) {
		Assert.notNull(entry, "entry must not be null");
		String uri = entry.resource().uri();
		if (this.resources.putIfAbsent(uri, entry) != null) {
			throw new IllegalArgumentException("Resource with URI '" + uri + "' already exists");
		}
	}

	private void put(SyncResourceTemplateEntry entry) {
		Assert.notNull(entry, "entry must not be null");
		String uriTemplate = entry.resourceTemplate().uriTemplate();
		// Compiled eagerly so that an invalid template fails at registration time.
		CompiledTemplate compiled = new CompiledTemplate(entry, this.uriTemplateManagerFactory.create(uriTemplate));
		if (this.templates.putIfAbsent(uriTemplate, compiled) != null) {
			throw new IllegalArgumentException("Resource template with URI '" + uriTemplate + "' already exists");
		}
	}

	private void broadcastListChanged() {
		if (this.changeBroadcaster != null) {
			this.changeBroadcaster.broadcast(new ServerChange.ResourcesListChanged());
		}
	}

	private record CompiledTemplate(SyncResourceTemplateEntry entry, McpUriTemplateManager matcher) {
	}

	/**
	 * Builder for {@link InMemorySyncResourceRepository}.
	 */
	public static final class Builder {

		private final List<SyncResourceEntry> resources = new ArrayList<>();

		private final List<SyncResourceTemplateEntry> resourceTemplates = new ArrayList<>();

		private McpUriTemplateManagerFactory uriTemplateManagerFactory = new DefaultMcpUriTemplateManagerFactory();

		private @Nullable McpChangeBroadcaster changeBroadcaster;

		private @Nullable Integer pageSize;

		private boolean subscribeSupported;

		private Builder() {
		}

		public Builder resource(SyncResourceEntry entry) {
			Assert.notNull(entry, "entry must not be null");
			this.resources.add(entry);
			return this;
		}

		public Builder resources(List<SyncResourceEntry> entries) {
			Assert.notNull(entries, "entries must not be null");
			this.resources.addAll(entries);
			return this;
		}

		public Builder resourceTemplate(SyncResourceTemplateEntry entry) {
			Assert.notNull(entry, "entry must not be null");
			this.resourceTemplates.add(entry);
			return this;
		}

		public Builder resourceTemplates(List<SyncResourceTemplateEntry> entries) {
			Assert.notNull(entries, "entries must not be null");
			this.resourceTemplates.addAll(entries);
			return this;
		}

		/**
		 * Compiles the URI templates that {@code resources/read} URIs are matched
		 * against. Defaults to the SDK's {@link DefaultMcpUriTemplateManagerFactory}.
		 */
		public Builder uriTemplateManagerFactory(McpUriTemplateManagerFactory factory) {
			Assert.notNull(factory, "factory must not be null");
			this.uriTemplateManagerFactory = factory;
			return this;
		}

		/**
		 * Receives {@code resources/list_changed} and {@code resources/updated}
		 * broadcasts. Unset, changes are not announced.
		 */
		public Builder changeBroadcaster(@Nullable McpChangeBroadcaster changeBroadcaster) {
			this.changeBroadcaster = changeBroadcaster;
			return this;
		}

		/**
		 * Whether the server advertises {@code resources.subscribe}, so that
		 * {@link InMemorySyncResourceRepository#resourceUpdated(String)} reaches
		 * subscribers.
		 */
		public Builder subscribeSupported(boolean subscribeSupported) {
			this.subscribeSupported = subscribeSupported;
			return this;
		}

		/**
		 * Maximum number of entries per {@code resources/list} and
		 * {@code resources/templates/list} page. Unset returns everything on one page.
		 */
		public Builder pageSize(@Nullable Integer pageSize) {
			this.pageSize = pageSize;
			return this;
		}

		public InMemorySyncResourceRepository build() {
			return new InMemorySyncResourceRepository(this);
		}

	}

}
