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

package org.springframework.ai.model.observation;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import io.micrometer.common.KeyValue;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import io.micrometer.observation.Observation;

import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.observation.conventions.AiObservationMetricAttributes;
import org.springframework.ai.observation.conventions.AiObservationMetricNames;
import org.springframework.ai.observation.conventions.AiTokenType;

/**
 * Generate metrics about the model usage in the context of an AI operation.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public final class ModelUsageMetricsGenerator {

	private static final String DESCRIPTION = "Measures number of input tokens, output tokens or billable duration used";

	private ModelUsageMetricsGenerator() {
	}

	public static void generate(Usage usage, Observation.Context context, MeterRegistry meterRegistry) {

		if (Objects.nonNull(usage.getDuration())) {
			Timer.builder(AiObservationMetricNames.OPERATION_DURATION.value())
				.description(DESCRIPTION)
				.tags(createTags(context))
				.register(meterRegistry)
				.record(Duration.ofSeconds(usage.getDuration()));
			return;
		}

		if (Objects.nonNull(usage.getPromptTokens())) {
			Counter.builder(AiObservationMetricNames.TOKEN_USAGE.value())
				.tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.INPUT.value())
				.description(DESCRIPTION)
				.tags(createTags(context))
				.register(meterRegistry)
				.increment(usage.getPromptTokens());
		}

		if (Objects.nonNull(usage.getCompletionTokens())) {
			Counter.builder(AiObservationMetricNames.TOKEN_USAGE.value())
				.tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.OUTPUT.value())
				.description(DESCRIPTION)
				.tags(createTags(context))
				.register(meterRegistry)
				.increment(usage.getCompletionTokens());
		}

		if (Objects.nonNull(usage.getTotalTokens())) {
			Counter.builder(AiObservationMetricNames.TOKEN_USAGE.value())
				.tag(AiObservationMetricAttributes.TOKEN_TYPE.value(), AiTokenType.TOTAL.value())
				.description(DESCRIPTION)
				.tags(createTags(context))
				.register(meterRegistry)
				.increment(usage.getTotalTokens());
		}

	}

	private static List<Tag> createTags(Observation.Context context) {
		List<Tag> tags = new ArrayList<>();
		for (KeyValue keyValue : context.getLowCardinalityKeyValues()) {
			tags.add(Tag.of(keyValue.getKey(), keyValue.getValue()));
		}
		return tags;
	}

}
