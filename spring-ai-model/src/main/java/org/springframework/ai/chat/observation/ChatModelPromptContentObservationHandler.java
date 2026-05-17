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

package org.springframework.ai.chat.observation;

import java.util.List;
import java.util.StringJoiner;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.handler.TracingObservationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.content.Content;
import org.springframework.ai.observation.ObservabilityHelper;
import org.springframework.util.CollectionUtils;

/**
 * Handler for emitting the chat prompt content to logs and as span attributes.
 *
 * @author Thomas Vitale
 * @author Jonatan Ivanov
 * @since 1.0.0
 */
public class ChatModelPromptContentObservationHandler implements ObservationHandler<ChatModelObservationContext> {

	private static final Logger logger = LoggerFactory.getLogger(ChatModelPromptContentObservationHandler.class);

	/**
	 * Span attribute key for input messages, following the OpenTelemetry GenAI
	 * semantic conventions.
	 */
	static final String GEN_AI_INPUT_MESSAGES = "gen_ai.input.messages";

	@Override
	public void onStop(ChatModelObservationContext context) {
		logger.info("Chat Model Prompt Content:\n{}", ObservabilityHelper.concatenateStrings(prompt(context)));
		sampleSpanTag(context);
	}

	private void sampleSpanTag(ChatModelObservationContext context) {
		TracingObservationHandler.TracingContext tracingContext = context.get(TracingObservationHandler.TracingContext.class);
		if (tracingContext == null) {
			return;
		}
		Span span = tracingContext.getSpan();
		if (span == null) {
			return;
		}
		List<Message> instructions = context.getRequest().getInstructions();
		if (CollectionUtils.isEmpty(instructions)) {
			return;
		}
		span.tag(GEN_AI_INPUT_MESSAGES, serializeMessages(instructions));
	}

	private String serializeMessages(List<Message> messages) {
		StringJoiner joiner = new StringJoiner(", ", "[", "]");
		for (Message message : messages) {
			String role = (message.getMessageType() != null) ? message.getMessageType().getValue() : "unknown";
			String content = (message.getText() != null) ? message.getText() : "";
			joiner.add("{\"role\":\"" + role + "\",\"content\":\"" + content + "\"}");
		}
		return joiner.toString();
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
