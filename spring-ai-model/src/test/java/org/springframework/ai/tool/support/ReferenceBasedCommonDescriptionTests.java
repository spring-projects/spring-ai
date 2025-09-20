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

package org.springframework.ai.tool.support;

import java.lang.reflect.Method;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for the reference-based common description functionality.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
class ReferenceBasedCommonDescriptionTests {

	@Test
	void shouldReturnOnlyBaseDescriptionForReferencedTools() throws Exception {
		Method method = ReferenceBasedCommonDescriptionExample.class.getMethod("getCurrentTime", String.class,
				String.class);
		String description = ToolUtils.getToolDescription(method);

		// Should only return the base description, not the common description
		assertThat(description).isEqualTo("get current time");
		assertThat(description).doesNotContain("Supported formats are 'yyyy-MM-dd'");
	}

	@Test
	void shouldReturnBaseDescriptionForToolsWithoutReference() throws Exception {
		Method method = ReferenceBasedCommonDescriptionExample.class.getMethod("simpleTool", String.class);
		String description = ToolUtils.getToolDescription(method);

		assertThat(description).isEqualTo("simple tool without common description");
	}

	@Test
	void shouldExtractClassCommonDescriptions() {
		String commonDescs = ToolUtils.getClassCommonDescriptions(ReferenceBasedCommonDescriptionExample.class);

		assertThat(commonDescs).contains("Supported formats are 'yyyy-MM-dd' for ISO standard");
		assertThat(commonDescs).contains("Input validation: All parameters must be non-null");
		assertThat(commonDescs).contains("Timezone must be a valid IANA timezone identifier");
	}

	@Test
	void shouldDetectClassHasCommonDescriptions() {
		boolean hasCommonDescs = ToolUtils.hasClassCommonDescriptions(ReferenceBasedCommonDescriptionExample.class);

		assertThat(hasCommonDescs).isTrue();
	}

	@Test
	void shouldWorkForMultipleToolsWithSameReference() throws Exception {
		Method method1 = ReferenceBasedCommonDescriptionExample.class.getMethod("getCurrentTime", String.class,
				String.class);
		Method method2 = ReferenceBasedCommonDescriptionExample.class.getMethod("formatTimestamp", long.class,
				String.class, String.class);

		String description1 = ToolUtils.getToolDescription(method1);
		String description2 = ToolUtils.getToolDescription(method2);

		// Both should have only their base descriptions
		assertThat(description1).isEqualTo("get current time");
		assertThat(description2).isEqualTo("format timestamp");

		// Neither should contain the common description
		assertThat(description1).doesNotContain("Supported formats");
		assertThat(description2).doesNotContain("Supported formats");
	}

	@Test
	void shouldWorkForToolsWithDifferentReferences() throws Exception {
		Method method1 = ReferenceBasedCommonDescriptionExample.class.getMethod("getCurrentTime", String.class,
				String.class);
		Method method2 = ReferenceBasedCommonDescriptionExample.class.getMethod("validateInput", String.class);

		String description1 = ToolUtils.getToolDescription(method1);
		String description2 = ToolUtils.getToolDescription(method2);

		assertThat(description1).isEqualTo("get current time");
		assertThat(description2).isEqualTo("validate input");
	}

}
