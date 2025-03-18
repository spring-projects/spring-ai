/*
* Copyright 2025 - 2025 the original author or authors.
*
* Licensed under the Apache License, Version 2.0 (the "License");
* you may not use this file except in compliance with the License.
* You may obtain a copy of the License at
*
* https://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package org.springframework.ai.mcp.server.autoconfigure;

import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;

import io.modelcontextprotocol.server.McpServerFeatures;
import io.modelcontextprotocol.server.McpSyncServerExchange;
import io.modelcontextprotocol.spec.McpSchema;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.CollectionUtils;

/**
 * @author Christian Tzolov
 */
@Deprecated
@Configuration
public class McpBackwardCompatibility {

	@Bean
	public List<BiConsumer<McpSyncServerExchange, List<McpSchema.Root>>> syncRootsChangeConsumerToHandler(
			List<Consumer<List<McpSchema.Root>>> rootsChangeConsumers) {

		if (CollectionUtils.isEmpty(rootsChangeConsumers)) {
			return List.of();
		}

		return rootsChangeConsumers.stream()
			.map(c -> (BiConsumer<McpSyncServerExchange, List<McpSchema.Root>>) ((exchange, roots) -> c.accept(roots)))
			.toList();
	}

	@Bean
	public List<McpServerFeatures.SyncToolSpecification> syncToolsRegistrationToSpecificaiton(
			ObjectProvider<List<McpServerFeatures.SyncToolRegistration>> toolRegistrations) {

		return toolRegistrations.stream()
			.flatMap(List::stream)
			.map(McpServerFeatures.SyncToolRegistration::toSpecification)
			.toList();
	}

	@Bean
	public List<McpServerFeatures.SyncResourceSpecification> syncResourceRegistrationToSpecificaiton(
			ObjectProvider<List<McpServerFeatures.SyncResourceRegistration>> resourceRegistrations) {

		return resourceRegistrations.stream()
			.flatMap(List::stream)
			.map(McpServerFeatures.SyncResourceRegistration::toSpecification)
			.toList();
	}

	@Bean
	public List<McpServerFeatures.SyncPromptSpecification> syncPromptRegistrationToSpecificaiton(
			ObjectProvider<List<McpServerFeatures.SyncPromptRegistration>> promptRegistrations) {

		return promptRegistrations.stream()
			.flatMap(List::stream)
			.map(McpServerFeatures.SyncPromptRegistration::toSpecification)
			.toList();
	}

	// Async
	@Bean
	public List<McpServerFeatures.AsyncToolSpecification> asyncToolsRegistrationToSpecificaiton(
			ObjectProvider<List<McpServerFeatures.AsyncToolRegistration>> toolRegistrations) {

		return toolRegistrations.stream()
			.flatMap(List::stream)
			.map(McpServerFeatures.AsyncToolRegistration::toSpecification)
			.toList();
	}

	@Bean
	public List<McpServerFeatures.AsyncResourceSpecification> asyncResourceRegistrationToSpecificaiton(
			ObjectProvider<List<McpServerFeatures.AsyncResourceRegistration>> resourceRegistrations) {

		return resourceRegistrations.stream()
			.flatMap(List::stream)
			.map(McpServerFeatures.AsyncResourceRegistration::toSpecification)
			.toList();
	}

	@Bean
	public List<McpServerFeatures.AsyncPromptSpecification> asyncPromptRegistrationToSpecificaiton(
			ObjectProvider<List<McpServerFeatures.AsyncPromptRegistration>> promptRegistrations) {

		return promptRegistrations.stream()
			.flatMap(List::stream)
			.map(McpServerFeatures.AsyncPromptRegistration::toSpecification)
			.toList();
	}

}
