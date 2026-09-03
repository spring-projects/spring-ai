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

package org.springframework.ai.audio.tts.observation;

import java.util.Objects;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;

import org.springframework.ai.model.observation.ModelUsageMetricsGenerator;

/**
 * Handler for generating metrics from text-to-speech model observations.
 *
 * @author Olivier Le Quellec
 * @since 2.0.2
 */
public class TextToSpeechModelMeterObservationHandler
		implements ObservationHandler<TextToSpeechModelObservationContext> {

	private final MeterRegistry meterRegistry;

	public TextToSpeechModelMeterObservationHandler(MeterRegistry meterRegistry) {
		this.meterRegistry = meterRegistry;
	}

	@Override
	public void onStop(TextToSpeechModelObservationContext context) {
		if (Objects.nonNull(context.getResponse())) {
			ModelUsageMetricsGenerator.generate(context.getResponse().getMetadata().getUsage(), context,
					this.meterRegistry);
		}
	}

	@Override
	public boolean supportsContext(Observation.Context context) {
		return context instanceof TextToSpeechModelObservationContext;
	}

}
