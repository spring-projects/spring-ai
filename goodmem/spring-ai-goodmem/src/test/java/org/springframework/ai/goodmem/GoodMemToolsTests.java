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

package org.springframework.ai.goodmem;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.tool.annotation.Tool;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Structural tests for the {@link Tool}-annotated surface of {@link GoodMemTools}. No
 * live GoodMem instance is required.
 */
class GoodMemToolsTests {

	private static final List<String> EXPECTED_TOOL_NAMES = List.of("goodmem_list_embedders", "goodmem_list_spaces",
			"goodmem_create_space", "goodmem_update_space", "goodmem_create_memory", "goodmem_retrieve_memories",
			"goodmem_get_memory", "goodmem_delete_memory");

	@Test
	void allExpectedToolsAreAnnotated() {
		List<String> actualNames = Arrays.stream(GoodMemTools.class.getDeclaredMethods())
			.map(method -> method.getAnnotation(Tool.class))
			.filter(annotation -> annotation != null)
			.map(Tool::name)
			.toList();
		assertThat(actualNames).containsExactlyInAnyOrderElementsOf(EXPECTED_TOOL_NAMES);
	}

	@Test
	void allToolsHaveDescriptions() {
		for (Method method : GoodMemTools.class.getDeclaredMethods()) {
			Tool tool = method.getAnnotation(Tool.class);
			if (tool == null) {
				continue;
			}
			assertThat(tool.description()).as("description for tool " + tool.name()).isNotBlank();
		}
	}

}
