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

import io.micrometer.observation.Observation;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.handler.TracingObservationHandler;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.observation.conventions.AiObservationAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link ChatModelCompletionSpanContentObservationHandler}.
 *
 * @author Jewoo Shin
 */
class ChatModelCompletionSpanContentObservationHandlerTests {

	private final ChatModelCompletionSpanContentObservationHandler observationHandler = new ChatModelCompletionSpanContentObservationHandler();

	@Test
	void whenNotSupportedObservationContextThenReturnFalse() {
		var context = new Observation.Context();
		assertThat(this.observationHandler.supportsContext(context)).isFalse();
	}

	@Test
	void whenSupportedObservationContextThenReturnTrue() {
		var context = chatModelObservationContext();
		assertThat(this.observationHandler.supportsContext(context)).isTrue();
	}

	@Test
	void whenCompletionWithTextThenTagSpan() {
		var context = chatModelObservationContext();
		context.setResponse(new ChatResponse(List.of(new Generation(new AssistantMessage("say please"),
				ChatGenerationMetadata.builder().finishReason("stop").build()))));
		Span span = withSpan(context);

		this.observationHandler.onStop(context);

		verify(span).tag(AiObservationAttributes.OUTPUT_MESSAGES.value(),
				"[{\"role\":\"assistant\",\"parts\":[{\"type\":\"text\",\"content\":\"say please\"}],\"finish_reason\":\"stop\"}]");
	}

	@Test
	void whenMultipleGenerationsThenTagAllMessages() {
		var context = chatModelObservationContext();
		context.setResponse(new ChatResponse(List.of(
				new Generation(new AssistantMessage("say please"),
						ChatGenerationMetadata.builder().finishReason("stop").build()),
				new Generation(new AssistantMessage("seriously, say please"),
						ChatGenerationMetadata.builder().finishReason("length").build()))));
		Span span = withSpan(context);

		this.observationHandler.onStop(context);

		verify(span).tag(AiObservationAttributes.OUTPUT_MESSAGES.value(),
				"[{\"role\":\"assistant\",\"parts\":[{\"type\":\"text\",\"content\":\"say please\"}],\"finish_reason\":\"stop\"},"
						+ "{\"role\":\"assistant\",\"parts\":[{\"type\":\"text\",\"content\":\"seriously, say please\"}],\"finish_reason\":\"length\"}]");
	}

	@Test
	void whenMissingFinishReasonThenNoOp() {
		var context = chatModelObservationContext();
		context.setResponse(new ChatResponse(List.of(new Generation(new AssistantMessage("say please")))));
		Span span = withSpan(context);

		this.observationHandler.onStop(context);

		verify(span, never()).tag(anyString(), anyString());
	}

	@Test
	void whenBlankFinishReasonThenNoOp() {
		var context = chatModelObservationContext();
		context.setResponse(new ChatResponse(List.of(new Generation(new AssistantMessage("say please"),
				ChatGenerationMetadata.builder().finishReason("").build()))));
		Span span = withSpan(context);

		this.observationHandler.onStop(context);

		verify(span, never()).tag(anyString(), anyString());
	}

	@Test
	void whenAnyGenerationMissingTextThenNoOp() {
		var context = chatModelObservationContext();
		context
			.setResponse(
					new ChatResponse(
							List.of(new Generation(new AssistantMessage("say please"),
									ChatGenerationMetadata.builder().finishReason("stop").build()),
									new Generation(
											AssistantMessage.builder()
												.content("")
												.toolCalls(List.of(new AssistantMessage.ToolCall("1", "function",
														"getWeather", "{}")))
												.build(),
											ChatGenerationMetadata.builder().finishReason("tool_calls").build()))));
		Span span = withSpan(context);

		this.observationHandler.onStop(context);

		verify(span, never()).tag(anyString(), anyString());
	}

	@Test
	void whenAnyGenerationMissingFinishReasonThenNoOp() {
		var context = chatModelObservationContext();
		context.setResponse(new ChatResponse(List.of(
				new Generation(new AssistantMessage("say please"),
						ChatGenerationMetadata.builder().finishReason("stop").build()),
				new Generation(new AssistantMessage("seriously, say please")))));
		Span span = withSpan(context);

		this.observationHandler.onStop(context);

		verify(span, never()).tag(anyString(), anyString());
	}

	@Test
	void whenEmptyResponseThenNoOp() {
		var context = chatModelObservationContext();
		Span span = withSpan(context);

		this.observationHandler.onStop(context);

		verify(span, never()).tag(anyString(), anyString());
	}

	@Test
	void whenEmptyCompletionThenNoOp() {
		var context = chatModelObservationContext();
		context.setResponse(new ChatResponse(List.of(new Generation(new AssistantMessage("")))));
		Span span = withSpan(context);

		this.observationHandler.onStop(context);

		verify(span, never()).tag(anyString(), anyString());
	}

	@Test
	void whenToolCallOnlyCompletionThenNoOp() {
		var context = chatModelObservationContext();
		context.setResponse(new ChatResponse(List.of(new Generation(AssistantMessage.builder()
			.content("")
			.toolCalls(List.of(new AssistantMessage.ToolCall("1", "function", "getWeather", "{}")))
			.build()))));
		Span span = withSpan(context);

		this.observationHandler.onStop(context);

		verify(span, never()).tag(anyString(), anyString());
	}

	@Test
	void whenTracingContextMissingThenNoOp() {
		var context = chatModelObservationContext();
		context.setResponse(new ChatResponse(List.of(new Generation(new AssistantMessage("say please")))));

		assertThatCode(() -> this.observationHandler.onStop(context)).doesNotThrowAnyException();
	}

	@Test
	void whenSpanMissingThenNoOp() {
		var context = chatModelObservationContext();
		context.setResponse(new ChatResponse(List.of(new Generation(new AssistantMessage("say please")))));
		context.put(TracingObservationHandler.TracingContext.class, new TracingObservationHandler.TracingContext());

		assertThatCode(() -> this.observationHandler.onStop(context)).doesNotThrowAnyException();
	}

	private ChatModelObservationContext chatModelObservationContext() {
		return ChatModelObservationContext.builder()
			.prompt(new Prompt("supercalifragilisticexpialidocious", ChatOptions.builder().model("mistral").build()))
			.provider("superprovider")
			.build();
	}

	private Span withSpan(ChatModelObservationContext context) {
		Span span = mock(Span.class);
		TracingObservationHandler.TracingContext tracingContext = new TracingObservationHandler.TracingContext();
		tracingContext.setSpan(span);
		context.put(TracingObservationHandler.TracingContext.class, tracingContext);
		return span;
	}

}
