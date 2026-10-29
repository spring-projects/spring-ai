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

package org.springframework.ai.mcp.annotation.provider.complete;

import java.lang.reflect.Method;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

import io.modelcontextprotocol.util.Assert;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.mcp.annotation.McpComplete;
import org.springframework.ai.mcp.annotation.adapter.CompleteAdapter;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.common.ModernMethodSupport;
import org.springframework.ai.mcp.annotation.method.complete.SyncModernMcpCompleteMethodCallback;
import org.springframework.ai.mcp.modern.McpSchemaConverter;
import org.springframework.ai.mcp.modern.SyncCompletionSpecification;

/**
 * Creates completion specifications for a 2026-07-28 ("modern") MCP server from methods
 * annotated with {@link McpComplete}. Each method is served by a
 * {@link SyncModernMcpCompleteMethodCallback}.
 *
 * @author Christian Tzolov
 * @since 2.2.0
 */
public class SyncModernMcpCompleteProvider {

	private static final Log logger = LogFactory.getLog(SyncModernMcpCompleteProvider.class);

	private final List<Object> completeObjects;

	public SyncModernMcpCompleteProvider(List<Object> completeObjects) {
		Assert.notNull(completeObjects, "completeObjects cannot be null");
		this.completeObjects = completeObjects;
	}

	public List<SyncCompletionSpecification> getModernCompleteSpecifications() {
		List<SyncCompletionSpecification> specifications = this.completeObjects.stream()
			.flatMap(bean -> Stream.of(this.doGetClassMethods(bean))
				.filter(method -> method.isAnnotationPresent(McpComplete.class))
				.filter(McpPredicates.filterReactiveReturnTypeMethod())
				.filter(ModernMethodSupport.filterLegacyContextMethods())
				.sorted(Comparator.comparing(Method::getName))
				.map(method -> {
					McpComplete annotation = method.getAnnotation(McpComplete.class);
					var reference = CompleteAdapter.asCompleteReference(annotation, method);
					SyncModernMcpCompleteMethodCallback callback = SyncModernMcpCompleteMethodCallback.builder()
						.method(method)
						.bean(bean)
						.complete(annotation)
						.build();
					return new SyncCompletionSpecification(McpSchemaConverter.toModern(reference), callback);
				}))
			.toList();

		if (specifications.isEmpty() && logger.isWarnEnabled()) {
			logger.warn("No complete methods found in the provided complete objects: " + this.completeObjects);
		}
		return specifications;
	}

	protected Method[] doGetClassMethods(Object bean) {
		return bean.getClass().getDeclaredMethods();
	}

}
