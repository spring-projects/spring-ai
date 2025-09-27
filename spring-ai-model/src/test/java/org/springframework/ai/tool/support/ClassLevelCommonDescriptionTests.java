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
 * Tests for the class-level common description functionality.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
class ClassLevelCommonDescriptionTests {

	@Test
	void shouldIncludeClassLevelCommonDescription() throws Exception {
		Method method = ClassLevelCommonDescriptionExample.class.getMethod("getCurrentTime", String.class,
				String.class);
		String description = ToolUtils.getToolDescription(method);

		assertThat(description).contains("Supported formats are 'yyyy-MM-dd' for ISO standard");
		assertThat(description).contains("Timezone must be a valid IANA timezone identifier");
		assertThat(description).contains("get current time");
	}

	@Test
	void shouldPrependCommonDescriptionToToolDescription() throws Exception {
		Method method = ClassLevelCommonDescriptionExample.class.getMethod("formatTimestamp", long.class, String.class,
				String.class);
		String description = ToolUtils.getToolDescription(method);

		// Common description should come first
		assertThat(description).startsWith("Supported formats are 'yyyy-MM-dd' for ISO standard");
		// Tool description should come after
		assertThat(description).contains("format timestamp");
	}

	@Test
	void shouldWorkForAllToolsInClass() throws Exception {
		Method method1 = ClassLevelCommonDescriptionExample.class.getMethod("getCurrentTime", String.class,
				String.class);
		Method method2 = ClassLevelCommonDescriptionExample.class.getMethod("formatTimestamp", long.class, String.class,
				String.class);
		Method method3 = ClassLevelCommonDescriptionExample.class.getMethod("parseDateTime", String.class,
				String.class);

		String description1 = ToolUtils.getToolDescription(method1);
		String description2 = ToolUtils.getToolDescription(method2);
		String description3 = ToolUtils.getToolDescription(method3);

		// All should have the common description
		assertThat(description1).contains("Supported formats are 'yyyy-MM-dd' for ISO standard");
		assertThat(description2).contains("Supported formats are 'yyyy-MM-dd' for ISO standard");
		assertThat(description3).contains("Supported formats are 'yyyy-MM-dd' for ISO standard");

		// But different tool descriptions
		assertThat(description1).contains("get current time");
		assertThat(description2).contains("format timestamp");
		assertThat(description3).contains("parse datetime string");
	}

}
