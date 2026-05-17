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
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * Unit tests for {@link ChatModelPromptContentObservationHandler}.
 *
 * @author Thomas Vitale
 * @author Jonatan Ivanov
 */
@ExtendWith(OutputCaptureExtension.class)
class ChatModelPromptContentObservationHandlerTests {

	private final ChatModelPromptContentObservationHandler observationHandler = new ChatModelPromptContentObservationHandler();

	@Test
	void whenNotSupportedObservationContextThenReturnFalse() {
		var context = new Observation.Context();
		assertThat(this.observationHandler.supportsContext(context)).isFalse();
	}

	@Test
	void whenSupportedObservationContextThenReturnTrue() {
		var context = ChatModelObservationContext.builder()
			.prompt(new Prompt(List.of(), ChatOptions.builder().model("mistral").build()))
			.provider("superprovider")
			.build();
		assertThat(this.observationHandler.supportsContext(context)).isTrue();
	}

	@Test
	void whenEmptyPromptThenOutputNothing(CapturedOutput output) {
		var context = ChatModelObservationContext.builder()
			.prompt(new Prompt(List.of(), ChatOptions.builder().model("mistral").build()))
			.provider("superprovider")
			.build();
		this.observationHandler.onStop(context);
		assertThat(output).contains("""
				INFO  o.s.a.c.o.ChatModelPromptContentObservationHandler -- Chat Model Prompt Content:
				[]
				""");
	}

	@Test
	void whenPromptWithTextThenOutputIt(CapturedOutput output) {
		var context = ChatModelObservationContext.builder()
			.prompt(new Prompt("supercalifragilisticexpialidocious", ChatOptions.builder().model("mistral").build()))
			.provider("superprovider")
			.build();
		this.observationHandler.onStop(context);
		assertThat(output).contains("""
				INFO  o.s.a.c.o.ChatModelPromptContentObservationHandler -- Chat Model Prompt Content:
				["supercalifragilisticexpialidocious"]
				""");
	}

	@Test
	void whenPromptWithMessagesThenOutputIt(CapturedOutput output) {
		var context = ChatModelObservationContext.builder()
			.prompt(new Prompt(
					List.of(new SystemMessage("you're a chimney sweep"),
							new UserMessage("supercalifragilisticexpialidocious")),
					ChatOptions.builder().model("mistral").build()))
			.provider("superprovider")
			.build();
		this.observationHandler.onStop(context);
		assertThat(output).contains("""
				INFO  o.s.a.c.o.ChatModelPromptContentObservationHandler -- Chat Model Prompt Content:
				["you're a chimney sweep", "supercalifragilisticexpialidocious"]
				""");
	}

	@Test
	void whenTracingContextPresentThenSpanTagIsSet() {
		Span span = mock(Span.class);
		TracingObservationHandler.TracingContext tracingContext = new TracingObservationHandler.TracingContext();
		tracingContext.setSpan(span);

		var context = ChatModelObservationContext.builder()
			.prompt(new Prompt(
					List.of(new SystemMessage("you're a chimney sweep"),
							new UserMessage("supercalifragilisticexpialidocious")),
					ChatOptions.builder().model("mistral").build()))
			.provider("superprovider")
			.build();
		context.put(TracingObservationHandler.TracingContext.class, tracingContext);

		this.observationHandler.onStop(context);

		verify(span).tag("gen_ai.input.messages",
				"[{\"role\":\"system\",\"content\":\"you're a chimney sweep\"},"
						+ "{\"role\":\"user\",\"content\":\"supercalifragilisticexpialidocious\"}]");
	}

	@Test
	void whenNoTracingContextThenNoSpanTagSet() {
		var context = ChatModelObservationContext.builder()
			.prompt(new Prompt("hello", ChatOptions.builder().model("mistral").build()))
			.provider("superprovider")
			.build();
		// No TracingContext in the observation context
		this.observationHandler.onStop(context);
		// No exception should be thrown
	}

	@Test
	void whenTracingContextPresentButEmptyPromptThenNoSpanTagSet() {
		Span span = mock(Span.class);
		TracingObservationHandler.TracingContext tracingContext = new TracingObservationHandler.TracingContext();
		tracingContext.setSpan(span);

		var context = ChatModelObservationContext.builder()
			.prompt(new Prompt(List.of(), ChatOptions.builder().model("mistral").build()))
			.provider("superprovider")
			.build();
		context.put(TracingObservationHandler.TracingContext.class, tracingContext);

		this.observationHandler.onStop(context);

		verifyNoInteractions(span);
	}

	@Test
	void whenTracingContextHasSpanWithNullThenNoSpanTagSet() {
		TracingObservationHandler.TracingContext tracingContext = new TracingObservationHandler.TracingContext();
		// span is null by default

		var context = ChatModelObservationContext.builder()
			.prompt(new Prompt("hello", ChatOptions.builder().model("mistral").build()))
			.provider("superprovider")
			.build();
		context.put(TracingObservationHandler.TracingContext.class, tracingContext);

		this.observationHandler.onStop(context);
		// No exception should be thrown
	}

	@Test
	void whenTracingContextPresentWithSingleMessageThenSpanTagIsSet() {
		Span span = mock(Span.class);
		TracingObservationHandler.TracingContext tracingContext = new TracingObservationHandler.TracingContext();
		tracingContext.setSpan(span);

		var context = ChatModelObservationContext.builder()
			.prompt(new Prompt("hello world", ChatOptions.builder().model("mistral").build()))
			.provider("superprovider")
			.build();
		context.put(TracingObservationHandler.TracingContext.class, tracingContext);

		this.observationHandler.onStop(context);

		verify(span).tag("gen_ai.input.messages",
				"[{\"role\":\"user\",\"content\":\"hello world\"}]");
	}

}
