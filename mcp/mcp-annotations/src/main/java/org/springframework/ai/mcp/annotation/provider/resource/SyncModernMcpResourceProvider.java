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

import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.util.Assert;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.common.MetaUtils;
import org.springframework.ai.mcp.annotation.common.ModernMethodSupport;
import org.springframework.ai.mcp.annotation.method.resource.SyncModernMcpResourceMethodCallback;
import org.springframework.ai.mcp.modern.McpSchemaConverter;
import org.springframework.ai.mcp.modern.SyncResourceSpecification;
import org.springframework.ai.mcp.modern.SyncResourceTemplateSpecification;

/**
 * Creates resource and resource template specifications for a 2026-07-28 ("modern") MCP
 * server from methods annotated with {@link McpResource}. Each method is served by a
 * {@link SyncModernMcpResourceMethodCallback}.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
public class SyncModernMcpResourceProvider {

	private static final Log logger = LogFactory.getLog(SyncModernMcpResourceProvider.class);

	private final List<Object> resourceObjects;

	public SyncModernMcpResourceProvider(List<Object> resourceObjects) {
		Assert.notNull(resourceObjects, "resourceObjects cannot be null");
		this.resourceObjects = resourceObjects;
	}

	public List<SyncResourceSpecification> getModernResourceSpecifications() {
		List<SyncResourceSpecification> specifications = resourceMethods(false).map(entry -> {
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
				.build();
			return new SyncResourceSpecification(McpSchemaConverter.toModern(resource), callback);
		}).toList();
		warnIfEmpty(specifications);
		return specifications;
	}

	public List<SyncResourceTemplateSpecification> getModernResourceTemplateSpecifications() {
		List<SyncResourceTemplateSpecification> specifications = resourceMethods(true).map(entry -> {
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
				.build();
			return new SyncResourceTemplateSpecification(McpSchemaConverter.toModern(template), callback);
		}).toList();
		warnIfEmpty(specifications);
		return specifications;
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
