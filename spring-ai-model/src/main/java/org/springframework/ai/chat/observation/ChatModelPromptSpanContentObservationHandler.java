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
import java.util.Map;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.handler.TracingObservationHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.observation.conventions.AiObservationAttributes;
import org.springframework.util.CollectionUtils;

/**
 * Handler for emitting the chat prompt content as span attributes. This handler requires
 * micrometer-tracing on the classpath and should only be registered when tracing is
 * available and the user has opted in to content capture (e.g., via
 * {@code spring.ai.chat.observations.log-prompt=true}).
 *
 * @author Thomas Vitale
 * @author Jonatan Ivanov
 * @since 1.0.0
 */
public class ChatModelPromptSpanContentObservationHandler implements ObservationHandler<ChatModelObservationContext> {

	private static final Logger logger = LoggerFactory.getLogger(ChatModelPromptSpanContentObservationHandler.class);

	private final ObjectMapper objectMapper;

	public ChatModelPromptSpanContentObservationHandler() {
		this.objectMapper = new ObjectMapper();
	}

	@Override
	public void onStop(ChatModelObservationContext context) {
		TracingObservationHandler.TracingContext tracingContext = context
			.get(TracingObservationHandler.TracingContext.class);
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
		span.tag(AiObservationAttributes.INPUT_MESSAGES.value(), serializeMessages(instructions));
	}

	private String serializeMessages(List<Message> messages) {
		try {
			List<Map<String, String>> entries = messages.stream().map(message -> {
				String role = (message.getMessageType() != null) ? message.getMessageType().getValue() : "unknown";
				String content = (message.getText() != null) ? message.getText() : "";
				return Map.of("role", role, "content", content);
			}).toList();
			return this.objectMapper.writeValueAsString(entries);
		}
		catch (Exception ex) {
			logger.warn("Failed to serialize messages for span attribute", ex);
			return "[]";
		}
	}

	@Override
	public boolean supportsContext(Observation.Context context) {
		return context instanceof ChatModelObservationContext;
	}

}
