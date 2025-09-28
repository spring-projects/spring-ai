/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.model.observation;

import java.util.HashMap;
import java.util.Map;

import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.observation.conventions.AiObservationMetricAttributes;
import org.springframework.ai.observation.conventions.AiObservationMetricNames;
import org.springframework.ai.observation.conventions.AiTokenType;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ModelUsageMetricsGenerator}.
 *
 * @author Thomas Vitale
 */
class ModelUsageMetricsGeneratorTests {

	@Test
	void whenTokenUsageThenMetrics() {
		var meterRegistry = new SimpleMeterRegistry();
		var usage = new TestUsage(1000, 500, 1500);
		ModelUsageMetricsGenerator.generate(usage, buildContext(), meterRegistry);

		assertThat(meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value()).meters()).hasSize(3);
		assertThat(meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.INPUT.value())
			.counter()
			.count()).isEqualTo(1000);
		assertThat(meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.OUTPUT.value())
			.counter()
			.count()).isEqualTo(500);
		assertThat(meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.TOTAL.value())
			.counter()
			.count()).isEqualTo(1500);
	}

	@Test
	void whenPartialTokenUsageThenMetrics() {
		var meterRegistry = new SimpleMeterRegistry();
		var usage = new TestUsage(1000, null, 1000);
		ModelUsageMetricsGenerator.generate(usage, buildContext(), meterRegistry);

		assertThat(meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value()).meters()).hasSize(2);
		assertThat(meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.INPUT.value())
			.counter()
			.count()).isEqualTo(1000);
		assertThat(meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.TOTAL.value())
			.counter()
			.count()).isEqualTo(1000);
	}

	private Observation.Context buildContext() {
		var context = new Observation.Context();
		context.addLowCardinalityKeyValue(KeyValue.of("key1", "value1"));
		context.addLowCardinalityKeyValue(KeyValue.of("key2", "value2"));
		return context;
	}

	@Test
	void whenZeroTokenUsageThenMetrics() {
		var meterRegistry = new SimpleMeterRegistry();
		var usage = new TestUsage(0, 0, 0);
		ModelUsageMetricsGenerator.generate(usage, buildContext(), meterRegistry);

		assertThat(meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value()).meters()).hasSize(3);
		assertThat(meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.INPUT.value())
			.counter()
			.count()).isEqualTo(0);
		assertThat(meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.OUTPUT.value())
			.counter()
			.count()).isEqualTo(0);
		assertThat(meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.TOTAL.value())
			.counter()
			.count()).isEqualTo(0);
	}

	@Test
	void whenBothPromptAndGenerationNullThenOnlyTotalMetric() {
		var meterRegistry = new SimpleMeterRegistry();
		var usage = new TestUsage(null, null, 100);
		ModelUsageMetricsGenerator.generate(usage, buildContext(), meterRegistry);

		assertThat(meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value()).meters()).hasSize(1);
		assertThat(meterRegistry.get(AiObservationMetricNames.TOKEN_USAGE.value())
			.tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.TOTAL.value())
			.counter()
			.count()).isEqualTo(100);
	}

	static class TestUsage implements Usage {

		private final Integer promptTokens;

		private final Integer generationTokens;

		private final int totalTokens;

		TestUsage(Integer promptTokens, Integer generationTokens, int totalTokens) {
			this.promptTokens = promptTokens;
			this.generationTokens = generationTokens;
			this.totalTokens = totalTokens;
		}

		@Override
		public Integer getPromptTokens() {
			return this.promptTokens;
		}

		@Override
		public Integer getCompletionTokens() {
			return this.generationTokens;
		}

		@Override
		public Integer getTotalTokens() {
			return this.totalTokens;
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
