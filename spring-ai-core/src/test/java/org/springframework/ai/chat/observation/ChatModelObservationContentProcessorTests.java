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

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.observation.conventions.AiProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit Tests for {@link ChatModelObservationContentProcessor}.
 *
 * @author John Blum
 */
@ExtendWith(MockitoExtension.class)
public class ChatModelObservationContentProcessorTests {

	@Mock
	private ChatOptions mockChatOptions;

	@Mock
	private Prompt mockPrompt;

	@Test
	void promptReturnsListOfMessageContent() {

		Prompt prompt = spy(new Prompt(List.of(new UserMessage("user"), new SystemMessage("system"))));

		ChatModelObservationContext context = ChatModelObservationContext.builder()
			.requestOptions(this.mockChatOptions)
			.provider(AiProvider.OPENAI.value())
			.prompt(prompt)
			.build();

		List<String> content = ChatModelObservationContentProcessor.prompt(context);

		assertThat(content).isNotNull().hasSize(2).containsExactly("user", "system");

		verify(prompt, times(1)).getInstructions();
	}

	@Test
	void promptWithNoMessagesReturnsEmptyList() {

		ChatModelObservationContext context = ChatModelObservationContext.builder()
			.requestOptions(this.mockChatOptions)
			.provider(AiProvider.OPENAI.value())
			.prompt(this.mockPrompt)
			.build();

		List<String> content = ChatModelObservationContentProcessor.prompt(context);

		assertThat(content).isNotNull().isEmpty();

		verify(this.mockPrompt, times(1)).getInstructions();
	}

	@Test
	void completionIsNullSafe() {

		List<String> completions = ChatModelObservationContentProcessor.completion(null);

		assertThat(completions).isNotNull().isEmpty();
	}

	@Test
	@SuppressWarnings("all")
	void completionsReturnsGeneratedResponse() {

		List<Generation> generations = List.of(generation(""), generation("one"), generation("  "), generation("two"),
				generation(null));

		ChatResponse response = ChatResponse.builder().withGenerations(generations).build();

		ChatModelObservationContext context = ChatModelObservationContext.builder()
			.requestOptions(this.mockChatOptions)
			.provider(AiProvider.OPENAI.value())
			.prompt(this.mockPrompt)
			.build();

		context.setResponse(response);

		List<String> completions = ChatModelObservationContentProcessor.completion(context);

		assertThat(completions).isNotNull().hasSize(2).containsExactly("one", "two");
	}

	@Test
	void completionsReturnsNoResponse() {

		ChatResponse response = ChatResponse.builder().withGenerations(Collections.emptyList()).build();

		ChatModelObservationContext context = ChatModelObservationContext.builder()
			.requestOptions(this.mockChatOptions)
			.provider(AiProvider.OPENAI.value())
			.prompt(this.mockPrompt)
			.build();

		context.setResponse(response);

		List<String> completions = ChatModelObservationContentProcessor.completion(context);

		assertThat(completions).isNotNull().isEmpty();
	}

	private AssistantMessage assistantMessage(String content) {
		return new AssistantMessage(content);
	}

	private Generation generation(String generatedContent) {
		return new Generation(assistantMessage(generatedContent));
	}

}
