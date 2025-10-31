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

package org.springframework.ai.mcp.client.common.autoconfigure;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.mcp.client.common.autoconfigure.aot.McpClientAutoConfigurationRuntimeHints;
import org.springframework.ai.mcp.client.common.autoconfigure.properties.McpStdioClientProperties;
import org.springframework.aot.hint.MemberCategory;
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

	private static final String MCP_CLIENT_PACKAGE = "org.springframework.ai.mcp.client.autoconfigure";

	private static final String JSON_PATTERN = "**.json";

	private RuntimeHints runtimeHints;

	private McpClientAutoConfigurationRuntimeHints mcpRuntimeHints;

	@BeforeEach
	void setUp() {
		this.runtimeHints = new RuntimeHints();
		this.mcpRuntimeHints = new McpClientAutoConfigurationRuntimeHints();
	}

	@Test
	void registerHints() throws IOException {

		this.mcpRuntimeHints.registerHints(this.runtimeHints, null);

		boolean hasJsonPattern = this.runtimeHints.resources()
			.resourcePatternHints()
			.anyMatch(resourceHints -> resourceHints.getIncludes()
				.stream()
				.anyMatch(pattern -> JSON_PATTERN.equals(pattern.getPattern())));

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

		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage(MCP_CLIENT_PACKAGE);

		Set<TypeReference> registeredTypes = new HashSet<>();
		this.runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		for (TypeReference jsonAnnotatedClass : jsonAnnotatedClasses) {
			assertThat(registeredTypes.contains(jsonAnnotatedClass))
				.as("JSON-annotated class %s should be registered for reflection", jsonAnnotatedClass.getName())
				.isTrue();
		}

		assertThat(registeredTypes.contains(TypeReference.of(McpStdioClientProperties.Parameters.class)))
			.as("McpStdioClientProperties.Parameters class should be registered")
			.isTrue();
	}

	@Test
	void registerHintsWithNullClassLoader() {
		// Test that registering hints with null ClassLoader works correctly
		this.mcpRuntimeHints.registerHints(this.runtimeHints, null);

		boolean hasJsonPattern = this.runtimeHints.resources()
			.resourcePatternHints()
			.anyMatch(resourceHints -> resourceHints.getIncludes()
				.stream()
				.anyMatch(pattern -> JSON_PATTERN.equals(pattern.getPattern())));

		assertThat(hasJsonPattern).as("The **.json resource pattern should be registered with null ClassLoader")
			.isTrue();
	}

	@Test
	void allMemberCategoriesAreRegistered() {
		this.mcpRuntimeHints.registerHints(this.runtimeHints, null);

		Set<TypeReference> jsonAnnotatedClasses = findJsonAnnotatedClassesInPackage(MCP_CLIENT_PACKAGE);

		// Verify that all MemberCategory values are registered for each type
		this.runtimeHints.reflection().typeHints().forEach(typeHint -> {
			if (jsonAnnotatedClasses.contains(typeHint.getType())) {
				Set<MemberCategory> expectedCategories = Set.of(MemberCategory.values());
				Set<MemberCategory> actualCategories = typeHint.getMemberCategories();
				assertThat(actualCategories.containsAll(expectedCategories)).isTrue();
			}
		});
	}

	@Test
	void verifySpecificMcpClientClasses() {
		this.mcpRuntimeHints.registerHints(this.runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		this.runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify specific MCP client classes are registered
		assertThat(registeredTypes.contains(TypeReference.of(McpStdioClientProperties.Parameters.class)))
			.as("McpStdioClientProperties.Parameters class should be registered")
			.isTrue();
	}

	@Test
	void multipleRegistrationCallsAreIdempotent() {
		// Register hints multiple times and verify no duplicates
		this.mcpRuntimeHints.registerHints(this.runtimeHints, null);
		int firstRegistrationCount = (int) this.runtimeHints.reflection().typeHints().count();

		this.mcpRuntimeHints.registerHints(this.runtimeHints, null);
		int secondRegistrationCount = (int) this.runtimeHints.reflection().typeHints().count();

		assertThat(firstRegistrationCount).isEqualTo(secondRegistrationCount);

		// Verify resource pattern registration is also idempotent
		boolean hasJsonPattern = this.runtimeHints.resources()
			.resourcePatternHints()
			.anyMatch(resourceHints -> resourceHints.getIncludes()
				.stream()
				.anyMatch(pattern -> JSON_PATTERN.equals(pattern.getPattern())));

		assertThat(hasJsonPattern).as("JSON pattern should still be registered after multiple calls").isTrue();
	}

	@Test
	void verifyJsonResourcePatternIsRegistered() {
		this.mcpRuntimeHints.registerHints(this.runtimeHints, null);

		// Verify the specific JSON resource pattern is registered
		boolean hasJsonPattern = this.runtimeHints.resources()
			.resourcePatternHints()
			.anyMatch(resourceHints -> resourceHints.getIncludes()
				.stream()
				.anyMatch(pattern -> JSON_PATTERN.equals(pattern.getPattern())));

		assertThat(hasJsonPattern).as("The **.json resource pattern should be registered").isTrue();
	}

	@Test
	void verifyNestedClassesAreRegistered() {
		this.mcpRuntimeHints.registerHints(this.runtimeHints, null);

		Set<TypeReference> registeredTypes = new HashSet<>();
		this.runtimeHints.reflection().typeHints().forEach(typeHint -> registeredTypes.add(typeHint.getType()));

		// Verify nested classes are properly registered
		assertThat(registeredTypes.contains(TypeReference.of(McpStdioClientProperties.Parameters.class)))
			.as("Nested Parameters class should be registered")
			.isTrue();
	}

	@Test
	void verifyResourcePatternHintsArePresentAfterRegistration() {
		this.mcpRuntimeHints.registerHints(this.runtimeHints, null);

		// Verify that resource pattern hints are present
		long patternCount = this.runtimeHints.resources().resourcePatternHints().count();
		assertThat(patternCount).isGreaterThan(0);
	}

}
