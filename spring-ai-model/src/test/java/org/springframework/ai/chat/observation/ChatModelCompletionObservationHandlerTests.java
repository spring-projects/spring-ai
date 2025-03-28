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

package org.springframework.ai.chat.observation;

import java.util.List;

import io.micrometer.tracing.handler.TracingObservationHandler;
import io.micrometer.tracing.otel.bridge.OtelCurrentTraceContext;
import io.micrometer.tracing.otel.bridge.OtelTracer;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.trace.ReadableSpan;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.observation.conventions.AiObservationAttributes;
import org.springframework.ai.observation.conventions.AiObservationEventNames;
import org.springframework.ai.observation.tracing.TracingHelper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ChatModelCompletionObservationHandler}.
 *
 * @author Thomas Vitale
 */
class ChatModelCompletionObservationHandlerTests {

	@Test
	void whenCompletionWithTextThenSpanEvent() {
		var observationContext = ChatModelObservationContext.builder()
			.prompt(new Prompt("supercalifragilisticexpialidocious"))
			.provider("mary-poppins")
			.requestOptions(ChatOptions.builder().model("spoonful-of-sugar").build())
			.build();
		observationContext.setResponse(new ChatResponse(List.of(new Generation(new AssistantMessage("say please")),
				new Generation(new AssistantMessage("seriously, say please")))));
		var sdkTracer = SdkTracerProvider.builder().build().get("test");
		var otelTracer = new OtelTracer(sdkTracer, new OtelCurrentTraceContext(), null);
		var span = otelTracer.nextSpan();
		var tracingContext = new TracingObservationHandler.TracingContext();
		tracingContext.setSpan(span);
		observationContext.put(TracingObservationHandler.TracingContext.class, tracingContext);

		new ChatModelCompletionObservationHandler().onStop(observationContext);

		var otelSpan = TracingHelper.extractOtelSpan(tracingContext);
		assertThat(otelSpan).isNotNull();
		var spanData = ((ReadableSpan) otelSpan).toSpanData();
		assertThat(spanData.getEvents().size()).isEqualTo(1);
		assertThat(spanData.getEvents().get(0).getName()).isEqualTo(AiObservationEventNames.CONTENT_COMPLETION.value());
		assertThat(spanData.getEvents()
			.get(0)
			.getAttributes()
			.get(AttributeKey.stringArrayKey(AiObservationAttributes.COMPLETION.value())))
			.containsOnly("say please", "seriously, say please");
	}

}
