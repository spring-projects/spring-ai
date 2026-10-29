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

package org.springframework.ai.mcp.annotation.provider.resource;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import io.modelcontextprotocol.json.McpJsonMapper;
import io.modelcontextprotocol.modern.server.feature.McpSyncResource;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.util.Assert;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.common.MetaUtils;
import org.springframework.ai.mcp.annotation.common.ModernMethodSupport;
import org.springframework.ai.mcp.annotation.method.resource.SyncModernMcpResourceMethodCallback;
import org.springframework.ai.mcp.modern.McpSchemaConverter;
import org.springframework.ai.mcp.modern.SyncResourceEntry;
import org.springframework.ai.mcp.modern.SyncResourceTemplateEntry;

/**
 * Creates resource and resource template entries for a 2026-07-28 ("modern") MCP server
 * from methods annotated with {@link McpResource}. Each method is served by a
 * {@link SyncModernMcpResourceMethodCallback} wrapped in a {@link McpSyncResource} that
 * requires no client capabilities.
 *
 * @author Christian Tzolov
 * @since 2.1.0
 */
public class SyncModernMcpResourceProvider {

	private static final Log logger = LogFactory.getLog(SyncModernMcpResourceProvider.class);

	private final List<Object> resourceObjects;

	private @Nullable McpJsonMapper jsonMapper;

	public SyncModernMcpResourceProvider(List<Object> resourceObjects) {
		Assert.notNull(resourceObjects, "resourceObjects cannot be null");
		this.resourceObjects = resourceObjects;
	}

	/**
	 * Sets the mapper the resource methods convert their MRTR input responses with,
	 * normally the server's. Unset, the SDK default is used.
	 */
	public void setJsonMapper(@Nullable McpJsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	public List<SyncResourceEntry> getResources() {
		List<SyncResourceEntry> resources = resourceMethods(false).map(entry -> {
			McpResource annotation = this.doGetMcpResourceAnnotation(entry.method());
			McpSchema.Resource resource = McpSchema.Resource.builder(annotation.uri(), name(entry.method(), annotation))
				.description(annotation.description())
				.mimeType(annotation.mimeType())
				.meta(MetaUtils.getMeta(annotation.metaProvider()))
				.build();
			SyncModernMcpResourceMethodCallback callback = SyncModernMcpResourceMethodCallback.builder()
				.method(entry.method())
				.bean(entry.bean())
				.ttlMs(annotation.ttlMs())
				.cacheScope(annotation.cacheScope())
				.resource(resource)
				.jsonMapper(this.jsonMapper)
				.build();
			return SyncResourceEntry.of(McpSchemaConverter.toModern(resource), callback);
		}).toList();
		warnIfEmpty(resources);
		return resources;
	}

	public List<SyncResourceTemplateEntry> getResourceTemplates() {
		List<SyncResourceTemplateEntry> templates = resourceMethods(true).map(entry -> {
			McpResource annotation = this.doGetMcpResourceAnnotation(entry.method());
			McpSchema.ResourceTemplate template = McpSchema.ResourceTemplate
				.builder(annotation.uri(), name(entry.method(), annotation))
				.description(annotation.description())
				.mimeType(annotation.mimeType())
				.meta(MetaUtils.getMeta(annotation.metaProvider()))
				.build();
			SyncModernMcpResourceMethodCallback callback = SyncModernMcpResourceMethodCallback.builder()
				.method(entry.method())
				.bean(entry.bean())
				.ttlMs(annotation.ttlMs())
				.cacheScope(annotation.cacheScope())
				.resource(template)
				.jsonMapper(this.jsonMapper)
				.build();
			return SyncResourceTemplateEntry.of(McpSchemaConverter.toModern(template), callback);
		}).toList();
		warnIfEmpty(templates);
		return templates;
	}

	private Stream<ResourceMethod> resourceMethods(boolean templates) {
		return this.resourceObjects.stream()
			.flatMap(bean -> Stream.of(this.doGetClassMethods(bean))
				.filter(method -> method.isAnnotationPresent(McpResource.class))
				.filter(McpPredicates.filterReactiveReturnTypeMethod())
				.filter(ModernMethodSupport.filterLegacyContextMethods())
				.filter(method -> McpPredicates
					.isUriTemplate(this.doGetMcpResourceAnnotation(method).uri()) == templates)
				.sorted(Comparator.comparing(Method::getName))
				.map(method -> new ResourceMethod(bean, method)));
	}

	private void warnIfEmpty(List<?> specifications) {
		if (specifications.isEmpty() && logger.isWarnEnabled()) {
			logger.warn("No resource methods found in the provided resource objects: " + this.resourceObjects);
		}
	}

	protected Method[] doGetClassMethods(Object bean) {
		return bean.getClass().getDeclaredMethods();
	}

	protected McpResource doGetMcpResourceAnnotation(Method method) {
		return method.getAnnotation(McpResource.class);
	}

	private static String name(Method method, McpResource resource) {
		return resource.name() == null || resource.name().isEmpty() ? method.getName() : resource.name();
	}

	private record ResourceMethod(Object bean, Method method) {
	}

}
