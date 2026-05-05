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
import java.util.Objects;
import java.util.function.BiFunction;
import java.util.stream.Stream;

import io.modelcontextprotocol.server.McpAsyncServerExchange;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncResourceSpecification;
import io.modelcontextprotocol.server.McpServerFeatures.AsyncResourceTemplateSpecification;
import io.modelcontextprotocol.spec.McpSchema;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceRequest;
import io.modelcontextprotocol.spec.McpSchema.ReadResourceResult;
import io.modelcontextprotocol.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import reactor.core.publisher.Mono;

import org.springframework.ai.mcp.annotation.McpResource;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.common.MetaUtils;
import org.springframework.ai.mcp.annotation.method.resource.AsyncMcpResourceMethodCallback;

/**
 * Provider for asynchronous MCP resource methods.
 *
 * This provider creates resource specifications for methods annotated with
 * {@link McpResource} that are designed to work with {@link McpAsyncServerExchange} and
 * return reactive types.
 *
 * @author Christian Tzolov
 * @author Alexandros Pappas
 * @author Vadzim Shurmialiou
 * @author Craig Walls
 */
public class AsyncMcpResourceProvider {

	private static final Logger logger = LoggerFactory.getLogger(AsyncMcpResourceProvider.class);

	private final List<Object> resourceObjects;

	/**
	 * Create a new AsyncMcpResourceProvider.
	 * @param resourceObjects the objects containing methods annotated with
	 * {@link McpResource}
	 */
	public AsyncMcpResourceProvider(List<Object> resourceObjects) {
		Assert.notNull(resourceObjects, "resourceObjects cannot be null");
		this.resourceObjects = resourceObjects;
	}

	/**
	 * Get the async resource specifications.
	 * @return the list of async resource specifications
	 */
	public List<AsyncResourceSpecification> getResourceSpecifications() {

		List<AsyncResourceSpecification> resourceSpecs = this.resourceObjects.stream()
			.map(resourceObject -> Stream.of(doGetClassMethods(resourceObject))
				.filter(method -> method.isAnnotationPresent(McpResource.class))
				.filter(McpPredicates.filterNonReactiveReturnTypeMethod())
				.sorted(Comparator.comparing(Method::getName))
				.map(mcpResourceMethod -> {

					var resourceAnnotation = doGetMcpResourceAnnotation(mcpResourceMethod);

					var uri = resourceAnnotation.uri();

					if (McpPredicates.isUriTemplate(uri)) {
						return null;
					}

					var name = getName(mcpResourceMethod, resourceAnnotation);
					var description = resourceAnnotation.description();
					var mimeType = resourceAnnotation.mimeType();
					var meta = MetaUtils.getMeta(resourceAnnotation.metaProvider());

					var mcpResource = McpSchema.Resource.builder(uri, name)
						.description(description)
						.mimeType(mimeType)
						.meta(meta)
						.build();

					BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> methodCallback = AsyncMcpResourceMethodCallback
						.builder()
						.method(mcpResourceMethod)
						.bean(resourceObject)
						.resource(mcpResource)
						.build();

					var resourceSpec = new AsyncResourceSpecification(mcpResource, methodCallback);

					return resourceSpec;
				})
				.filter(Objects::nonNull)
				.toList())
			.flatMap(List::stream)
			.toList();

		if (resourceSpecs.isEmpty()) {
			logger.warn("No resource methods found in the provided resource objects: {}", this.resourceObjects);
		}

		return resourceSpecs;
	}

	public List<AsyncResourceTemplateSpecification> getResourceTemplateSpecifications() {

		List<AsyncResourceTemplateSpecification> resourceSpecs = this.resourceObjects.stream()
			.map(resourceObject -> Stream.of(doGetClassMethods(resourceObject))
				.filter(method -> method.isAnnotationPresent(McpResource.class))
				.filter(McpPredicates.filterNonReactiveReturnTypeMethod())
				.sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
				.map(mcpResourceMethod -> {

					var resourceAnnotation = doGetMcpResourceAnnotation(mcpResourceMethod);

					var uri = resourceAnnotation.uri();

					if (!McpPredicates.isUriTemplate(uri)) {
						return null;
					}

					var name = getName(mcpResourceMethod, resourceAnnotation);
					var description = resourceAnnotation.description();
					var mimeType = resourceAnnotation.mimeType();
					var meta = MetaUtils.getMeta(resourceAnnotation.metaProvider());

					var mcpResourceTemplate = McpSchema.ResourceTemplate.builder(uri, name)
						.description(description)
						.mimeType(mimeType)
						.meta(meta)
						.build();

					BiFunction<McpAsyncServerExchange, ReadResourceRequest, Mono<ReadResourceResult>> methodCallback = AsyncMcpResourceMethodCallback
						.builder()
						.method(mcpResourceMethod)
						.bean(resourceObject)
						.resource(mcpResourceTemplate)
						.build();

					var resourceSpec = new AsyncResourceTemplateSpecification(mcpResourceTemplate, methodCallback);

					return resourceSpec;
				})
				.filter(Objects::nonNull)
				.toList())
			.flatMap(List::stream)
			.toList();

		if (resourceSpecs.isEmpty()) {
			logger.warn("No resource methods found in the provided resource objects: {}", this.resourceObjects);
		}

		return resourceSpecs;
	}

	protected Method[] doGetClassMethods(Object bean) {
		return bean.getClass().getDeclaredMethods();
	}

	protected McpResource doGetMcpResourceAnnotation(Method method) {
		return method.getAnnotation(McpResource.class);
	}

	private static String getName(Method method, McpResource resource) {
		Assert.notNull(method, "method cannot be null");
		if (resource == null || resource.name() == null || resource.name().isEmpty()) {
			return method.getName();
		}
		return resource.name();
	}

}
