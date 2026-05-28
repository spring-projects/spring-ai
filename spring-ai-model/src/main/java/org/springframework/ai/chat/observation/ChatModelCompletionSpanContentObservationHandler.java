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

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationHandler;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.handler.TracingObservationHandler;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.core.JacksonException;

import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.observation.conventions.AiObservationAttributes;
import org.springframework.ai.util.JsonHelper;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Handler for exposing the chat completion content as the {@code gen_ai.output.messages}
 * span attribute, following the OpenTelemetry GenAI semantic conventions.
 * <p>
 * The content is tagged directly on the tracing {@link Span}, when present, instead of
 * being propagated as a high-cardinality {@code KeyValue} on the
 * {@link Observation.Context}. This keeps the opt-in, potentially unbounded or
 * PII-bearing completion text isolated to the tracing channel rather than leaking into
 * metrics or other observation handlers, which is why this is a dedicated handler rather
 * than an {@code ObservationFilter}.
 * <p>
 * This handler exposes textual completions only. The semantic conventions require each
 * output message to map one-to-one to a generation and to carry a {@code finish_reason}.
 * To avoid emitting a malformed or misaligned attribute, the whole
 * {@code gen_ai.output.messages} attribute is skipped unless every generation can be
 * represented as a text message with a finish reason. Non-text output (e.g. tool calls)
 * is left for a follow-up.
 *
 * @author Jewoo Shin
 * @since 2.0.0
 */
public class ChatModelCompletionSpanContentObservationHandler
		implements ObservationHandler<ChatModelObservationContext> {

	private static final Logger logger = LoggerFactory
		.getLogger(ChatModelCompletionSpanContentObservationHandler.class);

	private static final JsonHelper jsonHelper = new JsonHelper();

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

		List<Map<String, Object>> messages = outputMessages(context);
		if (messages.isEmpty()) {
			return;
		}

		// Content span tagging is best-effort: a serialization failure in an
		// observability handler must not break the application flow.
		try {
			span.tag(AiObservationAttributes.OUTPUT_MESSAGES.value(), jsonHelper.toJson(messages));
		}
		catch (JacksonException ex) {
			logger.warn("Failed to serialize completion messages for the span attribute", ex);
		}
	}

	private List<Map<String, Object>> outputMessages(ChatModelObservationContext context) {
		if (context.getResponse() == null || CollectionUtils.isEmpty(context.getResponse().getResults())) {
			return List.of();
		}

		List<Map<String, Object>> messages = new ArrayList<>();
		for (Generation generation : context.getResponse().getResults()) {
			Map<String, Object> message = outputMessage(generation);
			// A generation that cannot be represented as a schema-valid text message
			// means
			// the attribute cannot be emitted without breaking the one-to-one mapping
			// between output messages and generations, so the whole attribute is skipped.
			if (message == null) {
				return List.of();
			}
			messages.add(message);
		}
		return messages;
	}

	private @Nullable Map<String, Object> outputMessage(Generation generation) {
		if (generation.getOutput() == null || !StringUtils.hasText(generation.getOutput().getText())) {
			return null;
		}
		String finishReason = generation.getMetadata().getFinishReason();
		if (!StringUtils.hasText(finishReason)) {
			return null;
		}

		Map<String, Object> part = new LinkedHashMap<>();
		part.put("type", "text");
		part.put("content", generation.getOutput().getText());

		Map<String, Object> message = new LinkedHashMap<>();
		message.put("role", "assistant");
		message.put("parts", List.of(part));
		message.put("finish_reason", finishReason);
		return message;
	}

	@Override
	public boolean supportsContext(Observation.Context context) {
		return context instanceof ChatModelObservationContext;
	}

}
