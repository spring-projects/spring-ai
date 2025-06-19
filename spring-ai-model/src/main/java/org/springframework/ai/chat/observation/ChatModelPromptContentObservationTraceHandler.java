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
import io.micrometer.tracing.handler.TracingObservationHandler;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import org.springframework.ai.chat.observation.trace.AiObservationContentFormatterName;
import org.springframework.ai.chat.observation.trace.LangfuseMessageFormatter;
import org.springframework.ai.chat.observation.trace.MessageFormatter;
import org.springframework.ai.chat.observation.trace.TextMessageFormatter;
import org.springframework.ai.content.Content;
import org.springframework.ai.observation.ObservabilityHelper;
import org.springframework.ai.observation.conventions.AiObservationAttributes;
import org.springframework.ai.observation.conventions.AiObservationEventNames;
import org.springframework.ai.observation.tracing.TracingHelper;
import org.springframework.util.CollectionUtils;

import java.util.List;

/**
 * Handler for emitting the chat prompt content to trace.
 *
 * @author tingchuan.li
 * @since 1.0.0
 */
public class ChatModelPromptContentObservationTraceHandler implements ObservationHandler<ChatModelObservationContext> {

	private final MessageFormatter messageFormatter;

	private final int tracePromptSize;

	public ChatModelPromptContentObservationTraceHandler(AiObservationContentFormatterName formatterName,
			int tracePromptSize) {
		this.tracePromptSize = tracePromptSize;
		if (formatterName == AiObservationContentFormatterName.LANGFUSE) {
			messageFormatter = new LangfuseMessageFormatter();
		}
		else {
			messageFormatter = new TextMessageFormatter();
		}
	}

	@Override
	public void onStop(ChatModelObservationContext context) {
		if (CollectionUtils.isEmpty(context.getRequest().getInstructions())) {
			return;
		}
		int skip = calculateSkips(context.getRequest().getInstructions().size(), this.tracePromptSize);
		List<String> prompt = context.getRequest()
			.getInstructions()
			.stream()
			.map(this.messageFormatter::format)
			.skip(skip)
			.toList();
		TracingObservationHandler.TracingContext tracingContext = context
			.getRequired(TracingObservationHandler.TracingContext.class);
		Span currentSpan = TracingHelper.extractOtelSpan(tracingContext);
		if (currentSpan != null) {
			currentSpan.addEvent(AiObservationEventNames.CONTENT_PROMPT.value(),
					Attributes.of(AttributeKey.stringArrayKey(AiObservationAttributes.PROMPT.value()), prompt));
		}
	}

	private int calculateSkips(int dataSize, int remainsSize) {
		if (remainsSize <= 0) {
			return 0;
		}
		if (dataSize <= 0) {
			return 0;
		}
		int skip = dataSize - remainsSize;
		return Math.max(skip, 0);
	}

	@Override
	public boolean supportsContext(Observation.Context context) {
		return context instanceof ChatModelObservationContext;
	}

}
