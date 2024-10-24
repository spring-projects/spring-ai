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

package org.springframework.ai.image.observation;

import java.util.StringJoiner;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;

import org.springframework.util.CollectionUtils;

/**
 * An {@link ObservationFilter} to include the image prompt content in the observation.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class ImageModelPromptContentObservationFilter implements ObservationFilter {

	@Override
	public Observation.Context map(Observation.Context context) {
		if (!(context instanceof ImageModelObservationContext imageModelObservationContext)) {
			return context;
		}

		if (CollectionUtils.isEmpty(imageModelObservationContext.getRequest().getInstructions())) {
			return imageModelObservationContext;
		}

		StringJoiner promptMessagesJoiner = new StringJoiner(", ", "[", "]");
		imageModelObservationContext.getRequest()
			.getInstructions()
			.forEach(message -> promptMessagesJoiner.add("\"" + message.getText() + "\""));

		imageModelObservationContext
			.addHighCardinalityKeyValue(ImageModelObservationDocumentation.HighCardinalityKeyNames.PROMPT
				.withValue(promptMessagesJoiner.toString()));

		return imageModelObservationContext;
	}

}
