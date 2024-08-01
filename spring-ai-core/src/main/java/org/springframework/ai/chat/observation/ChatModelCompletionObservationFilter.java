/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.chat.observation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

import java.util.StringJoiner;

/**
 * An {@link ObservationFilter} to include the chat completion content in the observation.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class ChatModelCompletionObservationFilter implements ObservationFilter {

	@Override
	public Observation.Context map(Observation.Context context) {
		if (!(context instanceof ChatModelObservationContext chatModelObservationContext)) {
			return context;
		}

		if (chatModelObservationContext.getResponse() == null
				|| chatModelObservationContext.getResponse().getResults() == null
				|| CollectionUtils.isEmpty(chatModelObservationContext.getResponse().getResults())) {
			return chatModelObservationContext;
		}

		StringJoiner completionChoicesJoiner = new StringJoiner(", ", "[", "]");
		chatModelObservationContext.getResponse()
			.getResults()
			.stream()
			.filter(generation -> generation.getOutput() != null
					&& StringUtils.hasText(generation.getOutput().getContent()))
			.forEach(generation -> completionChoicesJoiner.add("\"" + generation.getOutput().getContent() + "\""));

		if (StringUtils.hasText(chatModelObservationContext.getResponse().getResult().getOutput().getContent())) {
			chatModelObservationContext
				.addHighCardinalityKeyValue(ChatModelObservationDocumentation.HighCardinalityKeyNames.COMPLETION
					.withValue(completionChoicesJoiner.toString()));
		}

		return chatModelObservationContext;
	}

}
