/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.chat.observation;

import io.micrometer.common.KeyValue;
import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptionsBuilder;
import org.springframework.ai.chat.prompt.Prompt;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.chat.observation.ChatModelObservationDocumentation.HighCardinalityKeyNames;

/**
 * Unit tests for {@link ChatModelCompletionObservationFilter}.
 *
 * @author Thomas Vitale
 */
class ChatModelCompletionObservationFilterTests {

	private final ChatModelCompletionObservationFilter observationFilter = new ChatModelCompletionObservationFilter();

	@Test
	void whenNotSupportedObservationContextThenReturnOriginalContext() {
		var expectedContext = new Observation.Context();
		var actualContext = observationFilter.map(expectedContext);

		assertThat(actualContext).isEqualTo(expectedContext);
	}

	@Test
	void whenEmptyResponseThenReturnOriginalContext() {
		var expectedContext = ChatModelObservationContext.builder()
			.prompt(generatePrompt())
			.provider("superprovider")
			.requestOptions(ChatOptionsBuilder.builder().withModel("mistral").build())
			.build();
		var actualContext = observationFilter.map(expectedContext);

		assertThat(actualContext).isEqualTo(expectedContext);
	}

	@Test
	void whenEmptyCompletionThenReturnOriginalContext() {
		var expectedContext = ChatModelObservationContext.builder()
			.prompt(generatePrompt())
			.provider("superprovider")
			.requestOptions(ChatOptionsBuilder.builder().withModel("mistral").build())
			.build();
		expectedContext.setResponse(new ChatResponse(List.of(new Generation(new AssistantMessage("")))));
		var actualContext = observationFilter.map(expectedContext);

		assertThat(actualContext).isEqualTo(expectedContext);
	}

	@Test
	void whenCompletionWithTextThenAugmentContext() {
		var originalContext = ChatModelObservationContext.builder()
			.prompt(generatePrompt())
			.provider("superprovider")
			.requestOptions(ChatOptionsBuilder.builder().withModel("mistral").build())
			.build();
		originalContext.setResponse(new ChatResponse(List.of(new Generation(new AssistantMessage("say please")),
				new Generation(new AssistantMessage("seriously, say please")))));
		var augmentedContext = observationFilter.map(originalContext);

		assertThat(augmentedContext.getHighCardinalityKeyValues()).contains(KeyValue
			.of(HighCardinalityKeyNames.COMPLETION.asString(), "[\"say please\", \"seriously, say please\"]"));
	}

	private Prompt generatePrompt() {
		return new Prompt("supercalifragilisticexpialidocious");
	}

}
