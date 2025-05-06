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

package org.springframework.ai.model.chat.observation.autoconfigure;

import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
import io.micrometer.tracing.Tracer;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.observation.ChatModelCompletionObservationHandler;
import org.springframework.ai.chat.observation.ChatModelMeterObservationHandler;
import org.springframework.ai.chat.observation.ChatModelPromptContentObservationHandler;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.FilteredClassLoader;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ChatObservationAutoConfiguration}.
 *
 * @author Thomas Vitale
 * @author Jonatan Ivanov
 */
class ChatObservationAutoConfigurationTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ChatObservationAutoConfiguration.class));

	@Test
	void meterObservationHandlerEnabled() {
		this.contextRunner.withBean(CompositeMeterRegistry.class)
			.run(context -> assertThat(context).hasSingleBean(ChatModelMeterObservationHandler.class));
	}

	@Test
	void meterObservationHandlerDisabled() {
		this.contextRunner.run(context -> assertThat(context).doesNotHaveBean(ChatModelMeterObservationHandler.class));
	}

	@Test
	void promptHandlerDefault() {
		this.contextRunner
			.run(context -> assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationHandler.class));
	}

	@Test
	void promptHandlerEnabled() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withPropertyValues("spring.ai.chat.observations.log-prompt=true")
			.run(context -> assertThat(context).hasSingleBean(ChatModelPromptContentObservationHandler.class));
	}

	@Test
	void promptHandlerDisabled() {
		this.contextRunner.withPropertyValues("spring.ai.chat.observations.log-prompt=false")
			.run(context -> assertThat(context).doesNotHaveBean(ChatModelPromptContentObservationHandler.class));
	}

	@Test
	void completionHandlerDefault() {
		this.contextRunner
			.run(context -> assertThat(context).doesNotHaveBean(ChatModelCompletionObservationHandler.class));
	}

	@Test
	void completionHandlerEnabled() {
		this.contextRunner.withClassLoader(new FilteredClassLoader(Tracer.class))
			.withPropertyValues("spring.ai.chat.observations.log-completion=true")
			.run(context -> assertThat(context).hasSingleBean(ChatModelCompletionObservationHandler.class));
	}

	@Test
	void completionHandlerDisabled() {
		this.contextRunner.withPropertyValues("spring.ai.chat.observations.log-completion=false")
			.run(context -> assertThat(context).doesNotHaveBean(ChatModelCompletionObservationHandler.class));
	}

}
