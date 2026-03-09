/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.mcp.annotation.provider.progress;

import java.lang.reflect.Method;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

import io.modelcontextprotocol.spec.McpSchema.ProgressNotification;

import org.springframework.ai.mcp.annotation.McpProgress;
import org.springframework.ai.mcp.annotation.common.McpPredicates;
import org.springframework.ai.mcp.annotation.method.progress.SyncMcpProgressMethodCallback;
import org.springframework.ai.mcp.annotation.method.progress.SyncProgressSpecification;

/**
 * Provider for synchronous progress callbacks.
 *
 * <p>
 * This class scans a list of objects for methods annotated with {@link McpProgress} and
 * creates {@link Consumer} callbacks for them. These callbacks can be used to handle
 * progress notifications from MCP servers.
 *
 * <p>
 * Example usage: <pre>{@code
 * // Create a provider with a list of objects containing @McpProgress methods
 * SyncMcpProgressProvider provider = new SyncMcpProgressProvider(List.of(progressHandler));
 *
 * // Get the list of progress callbacks
 * List<SyncProgressSpecification> progressSpecs = provider.getProgressSpecifications();
 *
 * // Add the consumers to the client features
 * McpClientFeatures.Sync clientFeatures = new McpClientFeatures.Sync(
 *     clientInfo, clientCapabilities, roots,
 *     toolsChangeConsumers, resourcesChangeConsumers, promptsChangeConsumers,
 *     loggingConsumers, progressConsumers, samplingHandler);
 * }</pre>
 *
 * @author Christian Tzolov
 * @see McpProgress
 * @see SyncMcpProgressMethodCallback
 * @see ProgressNotification
 */
public class SyncMcpProgressProvider {

	private final List<Object> progressObjects;

	/**
	 * Create a new SyncMcpProgressProvider.
	 * @param progressObjects the objects containing methods annotated with
	 * {@link McpProgress}
	 */
	public SyncMcpProgressProvider(List<Object> progressObjects) {
		this.progressObjects = progressObjects != null ? progressObjects : List.of();
	}

	/**
	 * Get the list of progress specifications.
	 * @return the list of progress specifications
	 */
	public List<SyncProgressSpecification> getProgressSpecifications() {

		List<SyncProgressSpecification> progressConsumers = this.progressObjects.stream()
			.map(progressObject -> Stream.of(doGetClassMethods(progressObject))
				.filter(method -> method.isAnnotationPresent(McpProgress.class))
				.filter(McpPredicates.filterReactiveReturnTypeMethod())
				.filter(method -> method.getReturnType() == void.class) // Only void
																		// return type is
																		// valid for sync
				.sorted((m1, m2) -> m1.getName().compareTo(m2.getName()))
				.map(mcpProgressMethod -> {
					var progressAnnotation = mcpProgressMethod.getAnnotation(McpProgress.class);

					Consumer<ProgressNotification> methodCallback = SyncMcpProgressMethodCallback.builder()
						.method(mcpProgressMethod)
						.bean(progressObject)
						.progress(progressAnnotation)
						.build();

					return new SyncProgressSpecification(progressAnnotation.clients(), methodCallback);
				})
				.toList())
			.flatMap(List::stream)
			.toList();

		return progressConsumers;
	}

	/**
	 * Returns the methods of the given bean class.
	 * @param bean the bean instance
	 * @return the methods of the bean class
	 */
	protected Method[] doGetClassMethods(Object bean) {
		return bean.getClass().getDeclaredMethods();
	}

}
