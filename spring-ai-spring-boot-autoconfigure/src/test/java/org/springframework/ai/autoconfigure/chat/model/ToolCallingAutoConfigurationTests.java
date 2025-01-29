/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.autoconfigure.chat.model;

import org.junit.jupiter.api.Test;
import org.springframework.ai.model.tool.DefaultToolCallingManager;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.tool.execution.DefaultToolCallExceptionConverter;
import org.springframework.ai.tool.execution.ToolCallExceptionConverter;
import org.springframework.ai.tool.resolution.DelegatingToolCallbackResolver;
import org.springframework.ai.tool.resolution.ToolCallbackResolver;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ToolCallingAutoConfiguration}.
 *
 * @author Thomas Vitale
 */
class ToolCallingAutoConfigurationTests {

	@Test
	void beansAreCreated() {
		new ApplicationContextRunner().withConfiguration(AutoConfigurations.of(ToolCallingAutoConfiguration.class))
			.run(context -> {
				var toolCallbackResolver = context.getBean(ToolCallbackResolver.class);
				assertThat(toolCallbackResolver).isInstanceOf(DelegatingToolCallbackResolver.class);

				var toolCallExceptionConverter = context.getBean(ToolCallExceptionConverter.class);
				assertThat(toolCallExceptionConverter).isInstanceOf(DefaultToolCallExceptionConverter.class);

				var toolCallingManager = context.getBean(ToolCallingManager.class);
				assertThat(toolCallingManager).isInstanceOf(DefaultToolCallingManager.class);
			});
	}

}
