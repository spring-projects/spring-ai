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

import java.util.List;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.observation.ObservabilityHelper;
import org.springframework.util.StringUtils;

/**
 * Handler for emitting the chat client completion content to logs.
 *
 * @author Jonatan Ivanov
 * @since 1.1.0
 */
public class ChatClientCompletionObservationHandler implements ObservationHandler<ChatClientObservationContext> {

	private static final Logger logger = LoggerFactory.getLogger(ChatClientCompletionObservationHandler.class);

	@Override
	public void onStop(ChatClientObservationContext context) {
		logger.info("Chat Client Completion:\n{}", ObservabilityHelper.concatenateStrings(completion(context)));
	}

	private List<String> completion(ChatClientObservationContext context) {
		if (context.getResponse() == null || context.getResponse().chatResponse() == null) {
			return List.of();
		}

		return context.getResponse()
			.chatResponse()
			.getResults()
			.stream()
			.map(Generation::getOutput)
			.map(Message::getText)
			.filter(StringUtils::hasText)
			.toList();
	}

	@Override
	public boolean supportsContext(Observation.Context context) {
		return context instanceof ChatClientObservationContext;
	}

}
