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

package org.springframework.ai.model.anthropic.autoconfigure;

import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

import org.springframework.ai.anthropic.http.okhttp.AnthropicHttpClientBuilderCustomizer;
import org.springframework.ai.model.tool.autoconfigure.ToolCallingAutoConfiguration;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Verifies that {@link AnthropicHttpClientBuilderCustomizer} beans declared in the
 * application context are picked up by {@link AnthropicChatAutoConfiguration}.
 *
 * @author Ilayaperumal Gopinathan
 */
class AnthropicHttpClientBuilderCustomizerAutoConfigurationTests {

	private static final String COMMON_PROPS = "spring.ai.anthropic.api-key=test-key";

	@Test
	void customizerIsAppliedToChatModel() {
		AtomicInteger invocations = new AtomicInteger();
		new ApplicationContextRunner().withPropertyValues(COMMON_PROPS)
			.withConfiguration(
					AutoConfigurations.of(AnthropicChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.withBean(AnthropicHttpClientBuilderCustomizer.class, () -> builder -> invocations.incrementAndGet())
			.run(context -> assertThat(invocations.get())
				.as("customizer must be called twice — once for the sync client and once for the async client")
				.isEqualTo(2));
	}

	@Test
	void multipleCustomizersAreAllApplied() {
		AtomicInteger invocations = new AtomicInteger();
		new ApplicationContextRunner().withPropertyValues(COMMON_PROPS)
			.withConfiguration(
					AutoConfigurations.of(AnthropicChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.withBean("first", AnthropicHttpClientBuilderCustomizer.class,
					() -> builder -> invocations.incrementAndGet())
			.withBean("second", AnthropicHttpClientBuilderCustomizer.class,
					() -> builder -> invocations.incrementAndGet())
			.run(context -> assertThat(invocations.get())
				.as("each customizer must be called twice (once per client), so 2 customizers × 2 = 4")
				.isEqualTo(4));
	}

	@Test
	void noCustomizerBeanIsToleratedGracefully() {
		new ApplicationContextRunner().withPropertyValues(COMMON_PROPS)
			.withConfiguration(
					AutoConfigurations.of(AnthropicChatAutoConfiguration.class, ToolCallingAutoConfiguration.class))
			.run(context -> assertThat(context).hasNotFailed());
	}

}
