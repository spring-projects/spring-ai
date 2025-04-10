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

package org.springframework.ai.mcp.client.autoconfigure;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.client.autoconfigure.aot.McpClientAutoConfigurationRuntimeHints;
import org.springframework.ai.mcp.client.autoconfigure.properties.McpStdioClientProperties;
import org.springframework.aot.hint.RuntimeHints;
import org.springframework.aot.hint.TypeReference;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.springframework.ai.aot.AiRuntimeHints.findJsonAnnotatedClassesInPackage;

/**
 * @author Soby Chacko
 */
public class McpClientAutoConfigurationRuntimeHintsTests {

	@Test
	void registerHints() throws IOException {

		RuntimeHints runtimeHints = new RuntimeHints();

		McpClientAutoConfigurationRuntimeHints mcpRuntimeHints = new McpClientAutoConfigurationRuntimeHints();
		mcpRuntimeHints.registerHints(runtimeHints, null);

		boolean hasJsonPattern = runtimeHints.resources()
			.resourcePatternHints()
			.anyMatch(resourceHints -> resourceHints.getIncludes()
				.stream()
				.anyMatch(pattern -> "**.json".equals(pattern.getPattern())));

		assertThat(hasJsonPattern).as("The **.json resource pattern should be registered").isTrue();

		PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
		Resource[] resources = resolver.getResources("classpath*:**/*.json");

		assertThat(resources.length).isGreaterThan(1);

		boolean foundRootJson = false;
		boolean foundSubfolderJson = false;

		for (Resource resource : resources) {
			try {
				String path = resource.getURL().getPath();
				if (path.endsWith("/test-config.json")) {
					foundRootJson = true;
				}
				else if (path.endsWith("/nested/nested-config.json")) {
					foundSubfolderJson = true;
				}
			}
			catch (IOException e) {
				// nothing to do
			}
		}

		assertThat(foundRootJson).as("test-config.json should exist in the root test resources directory").isTrue();

		assertThat(foundSubfolderJson).as("nested-config.json should exist in the nested subfolder").isTrue();

		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage(
				"org.springframework.ai.mcp.client.autoconfigure");

		Set<TypeReference> registeredTypes = new HashSet<>();
		runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		for (TypeReference jsonAnnotatedClass : jsonAnnotatedClasses) {
			assertThat(registeredTypes.contains(jsonAnnotatedClass))
				.as("JSON-annotated class %s should be registered for reflection", jsonAnnotatedClass.getName())
				.isTrue();
		}

		assertThat(registeredTypes.contains(TypeReference.of(McpStdioClientProperties.Parameters.class)))
			.as("McpStdioClientProperties.Parameters class should be registered")
			.isTrue();
	}

}
