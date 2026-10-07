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

package org.springframework.ai.chat.client.advisor;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link ChatModelCallAdvisor}.
 *
 * @author Thomas Vitale
 * @author Filip Hrisafov
 */
class ChatModelCallAdvisorTests {

	@Test
	void whenChatModelIsNullThenThrow() {
		assertThatThrownBy(() -> ChatModelCallAdvisor.builder().chatModel(null).build())
			.isInstanceOf(IllegalStateException.class)
			.hasMessage("chatModel cannot be null");
	}

	@Test
	void whenNativeStructuredOutputNotSupportedAndNoOutputFormatThenUserMessageIsNotAugmented() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("{}")))));

		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(new Prompt("Tell me about John"))
			.context(ChatClientAttributes.STRUCTURED_OUTPUT_NATIVE.getKey(), true)
			.context(ChatClientAttributes.STRUCTURED_OUTPUT_SCHEMA.getKey(), "{\"type\":\"object\"}")
			.build();

		ChatModelCallAdvisor.builder().chatModel(chatModel).build().adviseCall(request, null);

		assertThat(promptCaptor.getValue().getUserMessage().getText()).isEqualTo("Tell me about John");
	}

	@Test
	void whenNativeStructuredOutputDisabledThenOutputFormatIsUsed() {
		ChatModel chatModel = mock(ChatModel.class);
		ArgumentCaptor<Prompt> promptCaptor = ArgumentCaptor.forClass(Prompt.class);
		given(chatModel.call(promptCaptor.capture()))
			.willReturn(new ChatResponse(List.of(new Generation(new AssistantMessage("{}")))));

		ChatClientRequest request = ChatClientRequest.builder()
			.prompt(new Prompt("Tell me about John", StructuredOutputChatOptions.builder().build()))
			.context(ChatClientAttributes.STRUCTURED_OUTPUT_NATIVE.getKey(), false)
			.context(ChatClientAttributes.STRUCTURED_OUTPUT_SCHEMA.getKey(), "{\"type\":\"object\"}")
			.context(ChatClientAttributes.OUTPUT_FORMAT.getKey(), "Respond in JSON")
			.build();

		ChatModelCallAdvisor.builder().chatModel(chatModel).build().adviseCall(request, null);

		Prompt prompt = promptCaptor.getValue();
		assertThat(prompt.getUserMessage().getText()).contains("Tell me about John", "Respond in JSON");
		assertThat(prompt.getOptions()).isInstanceOfSatisfying(StructuredOutputChatOptions.class,
				options -> assertThat(options.getOutputSchema()).isNull());
	}

}
