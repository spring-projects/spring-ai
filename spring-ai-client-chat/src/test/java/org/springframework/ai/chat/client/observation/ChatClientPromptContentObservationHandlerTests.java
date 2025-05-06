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

import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link ChatClientPromptContentObservationHandler}.
 *
 * @author Thomas Vitale
 * @author Jonatan Ivanov
 */
@ExtendWith(OutputCaptureExtension.class)
class ChatClientPromptContentObservationHandlerTests {

	private final ChatClientPromptContentObservationHandler observationHandler = new ChatClientPromptContentObservationHandler();

	@Test
	void whenNotSupportedObservationContextThenReturnFalse() {
		var context = new Observation.Context();
		assertThat(this.observationHandler.supportsContext(context)).isFalse();
	}

	@Test
	void whenSupportedObservationContextThenReturnTrue() {
		var context = ChatClientObservationContext.builder()
			.request(ChatClientRequest.builder().prompt(new Prompt(List.of())).build())
			.build();
		assertThat(this.observationHandler.supportsContext(context)).isTrue();
	}

	@Test
	void whenEmptyPromptThenOutputNothing(CapturedOutput output) {
		var context = ChatClientObservationContext.builder()
			.request(ChatClientRequest.builder().prompt(new Prompt(List.of())).build())
			.build();
		observationHandler.onStop(context);
		assertThat(output).contains("""
				Chat Client Prompt Content:
				[]
				""");
	}

	@Test
	void whenPromptWithTextThenOutputIt(CapturedOutput output) {
		var context = ChatClientObservationContext.builder()
			.request(ChatClientRequest.builder().prompt(new Prompt("supercalifragilisticexpialidocious")).build())
			.build();
		observationHandler.onStop(context);
		assertThat(output).contains("""
				Chat Client Prompt Content:
				["user":"supercalifragilisticexpialidocious"]
				""");
	}

	@Test
	void whenPromptWithMessagesThenOutputIt(CapturedOutput output) {
		var context = ChatClientObservationContext.builder()
			.request(ChatClientRequest.builder()
				.prompt(new Prompt(List.of(new SystemMessage("you're a chimney sweep"),
						new UserMessage("supercalifragilisticexpialidocious"))))
				.build())
			.build();
		observationHandler.onStop(context);
		assertThat(output).contains("""
				Chat Client Prompt Content:
				["system":"you're a chimney sweep", "user":"supercalifragilisticexpialidocious"]
				""");
	}

}
