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
import io.micrometer.observation.ObservationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.observation.ObservabilityHelper;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.Map;

/**
 * Handler for emitting the chat client prompt content to logs.
 *
 * @author Thomas Vitale
 * @author Jonatan Ivanov
 * @since 1.0.0
 */
public class ChatClientPromptContentObservationHandler implements ObservationHandler<ChatClientObservationContext> {

	private static final Logger logger = LoggerFactory.getLogger(ChatClientPromptContentObservationHandler.class);

	@Override
	public void onStop(ChatClientObservationContext context) {
		logger.debug("Chat Client Prompt Content:\n{}", ObservabilityHelper.concatenateEntries(processPrompt(context)));
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

	@Override
	public boolean supportsContext(Observation.Context context) {
		return context instanceof ChatClientObservationContext;
	}

}
