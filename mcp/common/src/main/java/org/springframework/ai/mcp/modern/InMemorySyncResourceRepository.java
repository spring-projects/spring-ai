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
import io.modelcontextprotocol.util.DefaultMcpUriTemplateManagerFactory;
import io.modelcontextprotocol.util.McpUriTemplateManagerFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * An {@link AbstractSyncResourceRepository} that keeps {@link SyncResourceSpecification}s
 * and {@link SyncResourceTemplateSpecification}s in sorted, concurrent maps. Resources
 * and templates can be added and removed at runtime; each change is broadcast as
 * {@code notifications/resources/list_changed} when a change broadcaster is configured,
 * and {@link #resourceUpdated(String)} notifies subscribers of a changed resource. Every
 * caller sees every resource unless a {@link McpVisibilityFilter} is set.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
public final class InMemorySyncResourceRepository extends AbstractSyncResourceRepository {

	private final Map<String, SyncResourceSpecification> resources = new ConcurrentSkipListMap<>();

	private final Map<String, SyncResourceTemplateSpecification> templates = new ConcurrentSkipListMap<>();

	private InMemorySyncResourceRepository(Builder builder) {
		super(builder.changeBroadcaster, builder.pageSize, builder.subscribeSupported, builder.visibility,
				builder.uriTemplateManagerFactory);
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
	public void add(SyncResourceSpecification specification) {
		put(specification);
		broadcastListChanged();
	}

	/**
	 * Adds a resource template and notifies listening clients.
	 * @throws IllegalArgumentException if a template with the same URI template exists
	 */
	public void add(SyncResourceTemplateSpecification specification) {
		put(specification);
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
		if (this.templates.remove(uri) != null) {
			evictMatcher(uri);
			removed = true;
		}
		if (removed) {
			broadcastListChanged();
		}
		return removed;
	}

	/**
	 * Notifies subscribers that the content of {@code uri} changed.
	 */
	public void resourceUpdated(String uri) {
		broadcastResourceUpdated(uri);
	}

	/**
	 * All resource definitions, sorted by URI.
	 */
	public List<Resource> resources() {
		List<Resource> result = new ArrayList<>(this.resources.size());
		this.resources.values().forEach(spec -> result.add(spec.resource()));
		return List.copyOf(result);
	}

	/**
	 * All resource template definitions, sorted by URI template.
	 */
	public List<ResourceTemplate> resourceTemplates() {
		return this.templates.values().stream().map(SyncResourceTemplateSpecification::resourceTemplate).toList();
	}

	@Override
	protected @Nullable SyncResourceSpecification lookup(McpRequestContext ctx, String uri) {
		return this.resources.get(uri);
	}

	@Override
	protected @Nullable SyncResourceTemplateSpecification lookupTemplate(McpRequestContext ctx, String uriTemplate) {
		return this.templates.get(uriTemplate);
	}

	@Override
	protected List<Resource> definitions(McpRequestContext ctx) {
		return resources();
	}

	@Override
	protected List<ResourceTemplate> templateDefinitions(McpRequestContext ctx) {
		return resourceTemplates();
	}

	private void put(SyncResourceSpecification specification) {
		Assert.notNull(specification, "specification must not be null");
		String uri = specification.resource().uri();
		if (this.resources.putIfAbsent(uri, specification) != null) {
			throw new IllegalArgumentException("Resource with URI '" + uri + "' already exists");
		}
	}

	private void put(SyncResourceTemplateSpecification specification) {
		Assert.notNull(specification, "specification must not be null");
		String uriTemplate = specification.resourceTemplate().uriTemplate();
		// Compile eagerly so that an invalid template fails at registration time.
		matcher(uriTemplate);
		if (this.templates.putIfAbsent(uriTemplate, specification) != null) {
			throw new IllegalArgumentException("Resource template with URI '" + uriTemplate + "' already exists");
		}
	}

	/**
	 * Builder for {@link InMemorySyncResourceRepository}.
	 */
	public static final class Builder {

		private final List<SyncResourceSpecification> resources = new ArrayList<>();

		private final List<SyncResourceTemplateSpecification> resourceTemplates = new ArrayList<>();

		private McpUriTemplateManagerFactory uriTemplateManagerFactory = new DefaultMcpUriTemplateManagerFactory();

		private @Nullable McpChangeBroadcaster changeBroadcaster;

		private @Nullable Integer pageSize;

		private @Nullable McpVisibilityFilter visibility;

		private boolean subscribeSupported;

		private Builder() {
		}

		public Builder resource(SyncResourceSpecification specification) {
			Assert.notNull(specification, "specification must not be null");
			this.resources.add(specification);
			return this;
		}

		public Builder resources(List<SyncResourceSpecification> specifications) {
			Assert.notNull(specifications, "specifications must not be null");
			this.resources.addAll(specifications);
			return this;
		}

		public Builder resourceTemplate(SyncResourceTemplateSpecification specification) {
			Assert.notNull(specification, "specification must not be null");
			this.resourceTemplates.add(specification);
			return this;
		}

		public Builder resourceTemplates(List<SyncResourceTemplateSpecification> specifications) {
			Assert.notNull(specifications, "specifications must not be null");
			this.resourceTemplates.addAll(specifications);
			return this;
		}

		public Builder uriTemplateManagerFactory(McpUriTemplateManagerFactory factory) {
			Assert.notNull(factory, "factory must not be null");
			this.uriTemplateManagerFactory = factory;
			return this;
		}

		public Builder changeBroadcaster(@Nullable McpChangeBroadcaster changeBroadcaster) {
			this.changeBroadcaster = changeBroadcaster;
			return this;
		}

		/**
		 * Restricts what each caller sees. Unset, every caller sees every resource and
		 * template.
		 */
		public Builder visibility(@Nullable McpVisibilityFilter visibility) {
			this.visibility = visibility;
			return this;
		}

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
