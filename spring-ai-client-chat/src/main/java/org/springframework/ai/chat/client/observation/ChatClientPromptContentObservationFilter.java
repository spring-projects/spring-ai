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

package org.springframework.ai.chat.client.observation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationFilter;
import org.springframework.ai.chat.observation.ChatModelObservationDocumentation;
import org.springframework.ai.observation.tracing.TracingHelper;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * An {@link ObservationFilter} to include the chat client prompt content in the
 * observation.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public final class ChatClientPromptContentObservationFilter implements ObservationFilter {

	@Override
	public Observation.Context map(Observation.Context context) {
		if (!(context instanceof ChatClientObservationContext chatClientObservationContext)) {
			return context;
		}

		var prompts = processPrompt(chatClientObservationContext);

		chatClientObservationContext
			.addHighCardinalityKeyValue(ChatModelObservationDocumentation.HighCardinalityKeyNames.PROMPT
				.withValue(TracingHelper.concatenateMaps(prompts)));

		return chatClientObservationContext;
	}

	private Map<String, Object> processPrompt(ChatClientObservationContext context) {
		if (CollectionUtils.isEmpty(context.getRequest().prompt().getInstructions())) {
			return Map.of();
		}

		var messages = new HashMap<String, Object>();
		context.getRequest()
			.prompt()
			.getInstructions()
			.forEach(message -> messages.put(message.getMessageType().getValue(), message.getText()));
		return messages;
	}

}
