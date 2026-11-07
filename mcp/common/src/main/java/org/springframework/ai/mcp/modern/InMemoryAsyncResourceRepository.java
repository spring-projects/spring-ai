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

import io.modelcontextprotocol.modern.McpSchema.Resource;
import io.modelcontextprotocol.modern.McpSchema.ResourceTemplate;
import io.modelcontextprotocol.modern.server.McpRequestContext;
import io.modelcontextprotocol.modern.server.feature.McpAsyncResource;
import io.modelcontextprotocol.modern.server.feature.McpAsyncResourceRepository;
import io.modelcontextprotocol.modern.server.feature.McpAsyncResourceTemplate;
import io.modelcontextprotocol.modern.server.feature.McpChangeBroadcaster;
import io.modelcontextprotocol.modern.server.feature.ResourceTemplatesPage;
import io.modelcontextprotocol.modern.server.feature.ResourcesPage;
import io.modelcontextprotocol.modern.server.feature.ServerChange;
import io.modelcontextprotocol.util.DefaultMcpUriTemplateManagerFactory;
import io.modelcontextprotocol.util.McpUriTemplateManagerFactory;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Mono;

import org.springframework.util.Assert;

/**
 * The asynchronous twin of {@link InMemorySyncResourceRepository}: a
 * {@link McpAsyncResourceRepository} that keeps {@link AsyncResourceRegistration}s and
 * {@link AsyncResourceTemplateRegistration}s in sorted, concurrent maps. A
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
 */
public final class InMemoryAsyncResourceRepository implements McpAsyncResourceRepository {

	private final Catalogue<AsyncResourceRegistration> resources = new Catalogue<>(
			registration -> registration.resource().uri(), "Resource", "URI");

	private final Catalogue<CompiledTemplate<AsyncResourceTemplateRegistration>> templates = new Catalogue<>(
			template -> template.registration().template().definition().uriTemplate(), "Resource template", "URI");

	private final McpUriTemplateManagerFactory uriTemplateManagerFactory;

	private final @Nullable McpChangeBroadcaster changeBroadcaster;

	private final @Nullable Integer pageSize;

	private final boolean subscribeSupported;

	private final boolean completionSupported;

	private InMemoryAsyncResourceRepository(Builder builder) {
		Assert.isTrue(builder.pageSize == null || builder.pageSize > 0, "pageSize must be positive");
		this.uriTemplateManagerFactory = builder.uriTemplateManagerFactory;
		this.changeBroadcaster = builder.changeBroadcaster;
		this.pageSize = builder.pageSize;
		this.subscribeSupported = builder.subscribeSupported;
		this.completionSupported = builder.completionSupported;
		builder.resources.forEach(this.resources::put);
		builder.resourceTemplates.forEach(this::put);
	}

	public static Builder builder() {
		return new Builder();
	}

	/**
	 * Adds a resource and notifies listening clients.
	 * @throws IllegalArgumentException if a resource with the same URI exists
	 */
	public void add(AsyncResourceRegistration registration) {
		this.resources.put(registration);
		broadcastListChanged();
	}

	/**
	 * Adds a resource template and notifies listening clients.
	 * @throws IllegalArgumentException if a template with the same URI template exists,
	 * or the URI template is invalid
	 */
	public void add(AsyncResourceTemplateRegistration registration) {
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
		boolean removed = this.resources.remove(uri);
		removed |= this.templates.remove(uri);
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
	public Mono<ResourcesPage> list(McpRequestContext ctx, @Nullable String cursor) {
		return Mono.fromSupplier(() -> {
			Pagination.Page<Resource> page = this.resources.page(AsyncResourceRegistration::resource, cursor,
					this.pageSize);
			return new ResourcesPage(page.items(), page.nextCursor(), null, null);
		});
	}

	@Override
	public Mono<ResourceTemplatesPage> listTemplates(McpRequestContext ctx, @Nullable String cursor) {
		return Mono.fromSupplier(() -> {
			Pagination.Page<ResourceTemplate> page = this.templates
				.page(template -> template.registration().template().definition(), cursor, this.pageSize);
			return new ResourceTemplatesPage(page.items(), page.nextCursor(), null, null);
		});
	}

	@Override
	public Mono<McpAsyncResource> find(McpRequestContext ctx, String uri) {
		return Mono.fromSupplier(() -> findHandler(uri));
	}

	/**
	 * The template registered under exactly this URI template, which carries the handler
	 * that completes its variables.
	 */
	@Override
	public Mono<McpAsyncResourceTemplate> findTemplate(McpRequestContext ctx, String uriTemplate) {
		return Mono.fromSupplier(() -> {
			CompiledTemplate<AsyncResourceTemplateRegistration> template = this.templates.get(uriTemplate);
			return template != null ? template.registration().template() : null;
		});
	}

	@Override
	public boolean supportsSubscribe() {
		return this.subscribeSupported;
	}

	@Override
	public boolean supportsCompletion() {
		return this.completionSupported;
	}

	private @Nullable McpAsyncResource findHandler(String uri) {
		AsyncResourceRegistration resource = this.resources.get(uri);
		if (resource != null) {
			return resource.handler();
		}
		for (CompiledTemplate<AsyncResourceTemplateRegistration> template : this.templates.values()) {
			if (template.matches(uri)) {
				return template.registration().handler();
			}
		}
		return null;
	}

	private void put(AsyncResourceTemplateRegistration registration) {
		Assert.notNull(registration, "registration must not be null");
		this.templates.put(CompiledTemplate.compile(registration, registration.template().definition().uriTemplate(),
				this.uriTemplateManagerFactory));
	}

	private void broadcastListChanged() {
		if (this.changeBroadcaster != null) {
			this.changeBroadcaster.broadcast(new ServerChange.ResourcesListChanged());
		}
	}

	/**
	 * Builder for {@link InMemoryAsyncResourceRepository}.
	 */
	public static final class Builder {

		private final List<AsyncResourceRegistration> resources = new ArrayList<>();

		private final List<AsyncResourceTemplateRegistration> resourceTemplates = new ArrayList<>();

		private McpUriTemplateManagerFactory uriTemplateManagerFactory = new DefaultMcpUriTemplateManagerFactory();

		private @Nullable McpChangeBroadcaster changeBroadcaster;

		private @Nullable Integer pageSize;

		private boolean subscribeSupported;

		private boolean completionSupported = true;

		private Builder() {
		}

		public Builder resource(AsyncResourceRegistration registration) {
			Assert.notNull(registration, "registration must not be null");
			this.resources.add(registration);
			return this;
		}

		public Builder resources(List<AsyncResourceRegistration> registrations) {
			Assert.notNull(registrations, "registrations must not be null");
			this.resources.addAll(registrations);
			return this;
		}

		public Builder resourceTemplate(AsyncResourceTemplateRegistration registration) {
			Assert.notNull(registration, "registration must not be null");
			this.resourceTemplates.add(registration);
			return this;
		}

		public Builder resourceTemplates(List<AsyncResourceTemplateRegistration> registrations) {
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
		 * {@link InMemoryAsyncResourceRepository#resourceUpdated(String)} reaches
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

		public InMemoryAsyncResourceRepository build() {
			return new InMemoryAsyncResourceRepository(this);
		}

	}

}
