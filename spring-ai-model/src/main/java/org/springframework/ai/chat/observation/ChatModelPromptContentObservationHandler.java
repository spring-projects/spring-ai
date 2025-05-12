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

package org.springframework.ai.chat.observation;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.content.Content;
import org.springframework.ai.observation.ObservabilityHelper;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * Handler for emitting the chat prompt content to logs.
 *
 * @author Thomas Vitale
 * @author Jonatan Ivanov
 * @since 1.0.0
 */
public class ChatModelPromptContentObservationHandler implements ObservationHandler<ChatModelObservationContext> {

	private static final Logger logger = LoggerFactory.getLogger(ChatModelPromptContentObservationHandler.class);

	@Override
	public void onStop(ChatModelObservationContext context) {
		logger.debug("Chat Model Prompt Content:\n{}", ObservabilityHelper.concatenateStrings(prompt(context)));
	}

	private List<String> prompt(ChatModelObservationContext context) {
		if (CollectionUtils.isEmpty(context.getRequest().getInstructions())) {
			return List.of();
		}

		return context.getRequest().getInstructions().stream().map(Content::getText).toList();
	}

	@Override
	public boolean supportsContext(Observation.Context context) {
		return context instanceof ChatModelObservationContext;
	}

}
