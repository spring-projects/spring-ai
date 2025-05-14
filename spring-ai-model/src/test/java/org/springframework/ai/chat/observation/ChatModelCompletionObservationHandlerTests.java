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
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ChatModelCompletionObservationHandler}.
 *
 * @author Thomas Vitale
 * @author Jonatan Ivanov
 */
@ExtendWith(OutputCaptureExtension.class)
class ChatModelCompletionObservationHandlerTests {

	private final ChatModelCompletionObservationHandler observationHandler = new ChatModelCompletionObservationHandler();

	@Test
	void whenNotSupportedObservationContextThenReturnFalse() {
		var context = new Observation.Context();
		assertThat(this.observationHandler.supportsContext(context)).isFalse();
	}

	@Test
	void whenSupportedObservationContextThenReturnTrue() {
		var context = ChatModelObservationContext.builder()
			.prompt(generatePrompt(ChatOptions.builder().model("mistral").build()))
			.provider("superprovider")
			.build();
		assertThat(this.observationHandler.supportsContext(context)).isTrue();
	}

	@Test
	void whenEmptyResponseThenOutputNothing(CapturedOutput output) {
		var context = ChatModelObservationContext.builder()
			.prompt(generatePrompt(ChatOptions.builder().model("mistral").build()))
			.provider("superprovider")
			.build();
		observationHandler.onStop(context);
		assertThat(output).contains("""
				Chat Model Completion:
				[]
				""");
	}

	@Test
	void whenEmptyCompletionThenOutputNothing(CapturedOutput output) {
		var context = ChatModelObservationContext.builder()
			.prompt(generatePrompt(ChatOptions.builder().model("mistral").build()))
			.provider("superprovider")
			.build();
		context.setResponse(new ChatResponse(List.of(new Generation(new AssistantMessage("")))));
		observationHandler.onStop(context);
		assertThat(output).contains("""
				Chat Model Completion:
				[]
				""");
	}

	@Test
	void whenCompletionWithTextThenOutputIt(CapturedOutput output) {
		var context = ChatModelObservationContext.builder()
			.prompt(generatePrompt(ChatOptions.builder().model("mistral").build()))
			.provider("superprovider")
			.build();
		context.setResponse(new ChatResponse(List.of(new Generation(new AssistantMessage("say please")),
				new Generation(new AssistantMessage("seriously, say please")))));
		observationHandler.onStop(context);
		assertThat(output).contains("""
				Chat Model Completion:
				["say please", "seriously, say please"]
				""");
	}

	private Prompt generatePrompt(ChatOptions chatOptions) {
		return new Prompt("supercalifragilisticexpialidocious", chatOptions);
	}

}
