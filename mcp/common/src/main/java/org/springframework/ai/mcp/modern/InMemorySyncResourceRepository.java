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
import io.modelcontextprotocol.modern.server.feature.McpSyncResourceTemplate;
import io.modelcontextprotocol.modern.server.feature.ResourceTemplatesPage;
import io.modelcontextprotocol.modern.server.feature.ResourcesPage;
import io.modelcontextprotocol.modern.server.feature.ServerChange;
import io.modelcontextprotocol.util.DefaultMcpUriTemplateManagerFactory;
import io.modelcontextprotocol.util.McpUriTemplateManagerFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * A {@link McpSyncResourceRepository} that keeps {@link SyncResourceRegistration}s and
 * {@link SyncResourceTemplateRegistration}s in sorted, concurrent maps. A
 * {@code resources/read} URI is served by the resource registered under it, or else by
 * the first template matching it in URI-template order. Resources and templates can be
 * added and removed at runtime; each change is broadcast as
 * {@code notifications/resources/list_changed} when a change broadcaster is configured,
 * and {@link #resourceUpdated(String)} notifies subscribers of a changed resource.
 * Listings are paged with an opaque key cursor when a page size is set. Completion is
 * served from the {@code completionHandler} of each template, and is advertised unless
 * the repository is built with {@code completionSupported(false)}.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 * @see InMemoryAsyncResourceRepository
 */
public final class InMemorySyncResourceRepository implements McpSyncResourceRepository {

	private final Map<String, SyncResourceRegistration> resources = new ConcurrentSkipListMap<>();

	private final Map<String, CompiledTemplate<SyncResourceTemplateRegistration>> templates = new ConcurrentSkipListMap<>();

	private final McpUriTemplateManagerFactory uriTemplateManagerFactory;

	private final @Nullable McpChangeBroadcaster changeBroadcaster;

	private final @Nullable Integer pageSize;

	private final boolean subscribeSupported;

	private final boolean completionSupported;

	private InMemorySyncResourceRepository(Builder builder) {
		Assert.isTrue(builder.pageSize == null || builder.pageSize > 0, "pageSize must be positive");
		this.uriTemplateManagerFactory = builder.uriTemplateManagerFactory;
		this.changeBroadcaster = builder.changeBroadcaster;
		this.pageSize = builder.pageSize;
		this.subscribeSupported = builder.subscribeSupported;
		this.completionSupported = builder.completionSupported;
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
	public void add(SyncResourceRegistration registration) {
		put(registration);
		broadcastListChanged();
	}

	/**
	 * Adds a resource template and notifies listening clients.
	 * @throws IllegalArgumentException if a template with the same URI template exists,
	 * or the URI template is invalid
	 */
	public void add(SyncResourceTemplateRegistration registration) {
		put(registration);
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
		Pagination.Page<Resource> page = Pagination.page(this.resources.values(),
				registration -> registration.resource().uri(), SyncResourceRegistration::resource, cursor,
				this.pageSize);
		return new ResourcesPage(page.items(), page.nextCursor(), null, null);
	}

	@Override
	public ResourceTemplatesPage listTemplates(McpRequestContext ctx, @Nullable String cursor) {
		Pagination.Page<ResourceTemplate> page = Pagination.page(this.templates.values(),
				template -> template.registration().template().definition().uriTemplate(),
				template -> template.registration().template().definition(), cursor, this.pageSize);
		return new ResourceTemplatesPage(page.items(), page.nextCursor(), null, null);
	}

	@Override
	public @Nullable McpSyncResource find(McpRequestContext ctx, String uri) {
		SyncResourceRegistration resource = this.resources.get(uri);
		if (resource != null) {
			return resource.handler();
		}
		for (CompiledTemplate<SyncResourceTemplateRegistration> template : this.templates.values()) {
			if (template.matches(uri)) {
				return template.registration().handler();
			}
		}
		return null;
	}

	/**
	 * The template registered under exactly this URI template, which carries the handler
	 * that completes its variables.
	 */
	@Override
	public @Nullable McpSyncResourceTemplate findTemplate(McpRequestContext ctx, String uriTemplate) {
		CompiledTemplate<SyncResourceTemplateRegistration> template = this.templates.get(uriTemplate);
		return template != null ? template.registration().template() : null;
	}

	@Override
	public boolean supportsSubscribe() {
		return this.subscribeSupported;
	}

	@Override
	public boolean supportsCompletion() {
		return this.completionSupported;
	}

	private void put(SyncResourceRegistration registration) {
		Assert.notNull(registration, "registration must not be null");
		String uri = registration.resource().uri();
		if (this.resources.putIfAbsent(uri, registration) != null) {
			throw new IllegalArgumentException("Resource with URI '" + uri + "' already exists");
		}
	}

	private void put(SyncResourceTemplateRegistration registration) {
		Assert.notNull(registration, "registration must not be null");
		String uriTemplate = registration.template().definition().uriTemplate();
		CompiledTemplate<SyncResourceTemplateRegistration> compiled = CompiledTemplate.compile(registration,
				uriTemplate, this.uriTemplateManagerFactory);
		if (this.templates.putIfAbsent(uriTemplate, compiled) != null) {
			throw new IllegalArgumentException("Resource template with URI '" + uriTemplate + "' already exists");
		}
	}

	private void broadcastListChanged() {
		if (this.changeBroadcaster != null) {
			this.changeBroadcaster.broadcast(new ServerChange.ResourcesListChanged());
		}
	}

	/**
	 * Builder for {@link InMemorySyncResourceRepository}.
	 */
	public static final class Builder {

		private final List<SyncResourceRegistration> resources = new ArrayList<>();

		private final List<SyncResourceTemplateRegistration> resourceTemplates = new ArrayList<>();

		private McpUriTemplateManagerFactory uriTemplateManagerFactory = new DefaultMcpUriTemplateManagerFactory();

		private @Nullable McpChangeBroadcaster changeBroadcaster;

		private @Nullable Integer pageSize;

		private boolean subscribeSupported;

		private boolean completionSupported = true;

		private Builder() {
		}

		public Builder resource(SyncResourceRegistration registration) {
			Assert.notNull(registration, "registration must not be null");
			this.resources.add(registration);
			return this;
		}

		public Builder resources(List<SyncResourceRegistration> registrations) {
			Assert.notNull(registrations, "registrations must not be null");
			this.resources.addAll(registrations);
			return this;
		}

		public Builder resourceTemplate(SyncResourceTemplateRegistration registration) {
			Assert.notNull(registration, "registration must not be null");
			this.resourceTemplates.add(registration);
			return this;
		}

		public Builder resourceTemplates(List<SyncResourceTemplateRegistration> registrations) {
			Assert.notNull(registrations, "registrations must not be null");
			this.resourceTemplates.addAll(registrations);
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
		 * Whether the server serves {@code completion/complete} for the templates of this
		 * repository and advertises {@code completions}. Defaults to {@code true}; a
		 * template without a completion handler then completes to no values.
		 */
		public Builder completionSupported(boolean completionSupported) {
			this.completionSupported = completionSupported;
			return this;
		}

		/**
		 * Maximum number of items per {@code resources/list} and
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
