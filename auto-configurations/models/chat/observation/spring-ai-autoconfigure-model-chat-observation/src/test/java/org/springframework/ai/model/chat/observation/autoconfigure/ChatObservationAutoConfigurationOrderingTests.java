/*
 * Copyright 2023-2026 the original author or authors.
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

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.observation.ChatModelMeterObservationHandler;
import org.springframework.ai.chat.observation.ChatModelObservationContext;
import org.springframework.ai.chat.observation.DefaultChatModelObservationConvention;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.observation.conventions.AiObservationMetricNames;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.micrometer.metrics.autoconfigure.CompositeMeterRegistryAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.MetricsAutoConfiguration;
import org.springframework.boot.micrometer.metrics.autoconfigure.export.simple.SimpleMetricsExportAutoConfiguration;
import org.springframework.boot.micrometer.observation.autoconfigure.ObservationAutoConfiguration;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests that verify {@link ChatObservationAutoConfiguration} correctly creates the
 * {@link ChatModelMeterObservationHandler} bean when loaded alongside the real Spring
 * Boot auto-configuration chain (not manually injected MeterRegistry).
 * <p>
 * This validates that the {@code @AutoConfiguration(afterName = ...)} ordering is
 * correct, ensuring the {@code @ConditionalOnBean(MeterRegistry.class)} condition is
 * satisfied. See https://github.com/spring-projects/spring-ai/issues/5444
 *
 * @author Soby Chacko
 */
class ChatObservationAutoConfigurationOrderingTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(ObservationAutoConfiguration.class, MetricsAutoConfiguration.class,
				CompositeMeterRegistryAutoConfiguration.class, SimpleMetricsExportAutoConfiguration.class,
				ChatObservationAutoConfiguration.class));

	@Test
	void meterObservationHandlerCreatedWithFullAutoConfigChain() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(MeterRegistry.class);
			assertThat(context).hasSingleBean(ChatModelMeterObservationHandler.class);
		});
	}

	@Test
	void tokenUsageMetricGeneratedWithFullAutoConfigChain() {
		this.contextRunner.run(context -> {
			assertThat(context).hasSingleBean(MeterRegistry.class);
			assertThat(context).hasSingleBean(ObservationRegistry.class);

			MeterRegistry meterRegistry = context.getBean(MeterRegistry.class);
			ObservationRegistry observationRegistry = context.getBean(ObservationRegistry.class);

			ChatModelObservationContext observationContext = ChatModelObservationContext.builder()
				.prompt(new Prompt("test", ChatOptions.builder().model("test-model").build()))
				.provider("test-provider")
				.build();

			Observation observation = Observation.createNotStarted(new DefaultChatModelObservationConvention(),
					() -> observationContext, observationRegistry);
			observation.start();

			observationContext.setResponse(new ChatResponse(List.of(new Generation(new AssistantMessage("response"))),
					ChatResponseMetadata.builder().model("test-model").usage(new TestUsage()).build()));

			observation.stop();

			assertThat(meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value()).meters()).hasSize(3);
		});
	}

	static class TestUsage implements Usage {

		@Override
		public Integer getPromptTokens() {
			return 100;
		}

		@Override
		public Integer getCompletionTokens() {
			return 50;
		}

		@Override
		public Map<String, Integer> getNativeUsage() {
			Map<String, Integer> usage = new HashMap<>();
			usage.put("promptTokens", getPromptTokens());
			usage.put("completionTokens", getCompletionTokens());
			usage.put("totalTokens", getTotalTokens());
			return usage;
		}

	}

}
