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

package org.springframework.ai.chat.client.advisor.api;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.client.ChatClientAttributes;
import org.springframework.ai.chat.client.ChatClientRequest;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.tool.ToolCallback;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link AdvisedRequest}.
 *
 * @author Thomas Vitale
 */
class AdvisedRequestTests {

	@Test
	void buildAdvisedRequest() {
		AdvisedRequest request = new AdvisedRequest(mock(ChatModel.class), "user", null, null, List.of(), List.of(),
				List.of(), List.of(), Map.of(), Map.of(), List.of(), Map.of(), Map.of(), Map.of());
		assertThat(request).isNotNull();
	}

	@Test
	void whenChatModelIsNullThenThrows() {
		assertThatThrownBy(() -> new AdvisedRequest(null, "user", null, null, List.of(), List.of(), List.of(),
				List.of(), Map.of(), Map.of(), List.of(), Map.of(), Map.of(), Map.of()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("chatModel cannot be null");
	}

	@Test
	void whenUserTextIsNullThenThrows() {
		assertThatThrownBy(() -> new AdvisedRequest(mock(ChatModel.class), null, null, null, List.of(), List.of(),
				List.of(), List.of(), Map.of(), Map.of(), List.of(), Map.of(), Map.of(), Map.of()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage(
					"userText cannot be null or empty unless messages are provided and contain Tool Response message.");
	}

	@Test
	void whenUserTextIsEmptyThenThrows() {
		assertThatThrownBy(() -> new AdvisedRequest(mock(ChatModel.class), "", null, null, List.of(), List.of(),
				List.of(), List.of(), Map.of(), Map.of(), List.of(), Map.of(), Map.of(), Map.of()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage(
					"userText cannot be null or empty unless messages are provided and contain Tool Response message.");
	}

	@Test
	void whenMediaIsNullThenThrows() {
		assertThatThrownBy(() -> new AdvisedRequest(mock(ChatModel.class), "user", null, null, null, List.of(),
				List.of(), List.of(), Map.of(), Map.of(), List.of(), Map.of(), Map.of(), Map.of()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("media cannot be null");
	}

	@Test
	void whenFunctionNamesIsNullThenThrows() {
		assertThatThrownBy(() -> new AdvisedRequest(mock(ChatModel.class), "user", null, null, List.of(), null,
				List.of(), List.of(), Map.of(), Map.of(), List.of(), Map.of(), Map.of(), Map.of()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("functionNames cannot be null");
	}

	@Test
	void whenFunctionCallbacksIsNullThenThrows() {
		assertThatThrownBy(() -> new AdvisedRequest(mock(ChatModel.class), "user", null, null, List.of(), List.of(),
				null, List.of(), Map.of(), Map.of(), List.of(), Map.of(), Map.of(), Map.of()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("functionCallbacks cannot be null");
	}

	@Test
	void whenMessagesIsNullThenThrows() {
		assertThatThrownBy(() -> new AdvisedRequest(mock(ChatModel.class), "user", null, null, List.of(), List.of(),
				List.of(), null, Map.of(), Map.of(), List.of(), Map.of(), Map.of(), Map.of()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("messages cannot be null");
	}

	@Test
	void whenUserParamsIsNullThenThrows() {
		assertThatThrownBy(() -> new AdvisedRequest(mock(ChatModel.class), "user", null, null, List.of(), List.of(),
				List.of(), List.of(), null, Map.of(), List.of(), Map.of(), Map.of(), Map.of()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("userParams cannot be null");
	}

	@Test
	void whenSystemParamsIsNullThenThrows() {
		assertThatThrownBy(() -> new AdvisedRequest(mock(ChatModel.class), "user", null, null, List.of(), List.of(),
				List.of(), List.of(), Map.of(), null, List.of(), Map.of(), Map.of(), Map.of()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("systemParams cannot be null");
	}

	@Test
	void whenAdvisorsIsNullThenThrows() {
		assertThatThrownBy(() -> new AdvisedRequest(mock(ChatModel.class), "user", null, null, List.of(), List.of(),
				List.of(), List.of(), Map.of(), Map.of(), null, Map.of(), Map.of(), Map.of()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("advisors cannot be null");
	}

	@Test
	void whenAdvisorParamsIsNullThenThrows() {
		assertThatThrownBy(() -> new AdvisedRequest(mock(ChatModel.class), "user", null, null, List.of(), List.of(),
				List.of(), List.of(), Map.of(), Map.of(), List.of(), null, Map.of(), Map.of()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("advisorParams cannot be null");
	}

	@Test
	void whenAdviseContextIsNullThenThrows() {
		assertThatThrownBy(() -> new AdvisedRequest(mock(ChatModel.class), "user", null, null, List.of(), List.of(),
				List.of(), List.of(), Map.of(), Map.of(), List.of(), Map.of(), null, Map.of()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("adviseContext cannot be null");
	}

	@Test
	void whenToolContextIsNullThenThrows() {
		assertThatThrownBy(() -> new AdvisedRequest(mock(ChatModel.class), "user", null, null, List.of(), List.of(),
				List.of(), List.of(), Map.of(), Map.of(), List.of(), Map.of(), Map.of(), null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("toolContext cannot be null");
	}

	@Test
	void whenConvertToAndFromChatClientRequest() {
		ChatModel chatModel = mock(ChatModel.class);
		ChatOptions chatOptions = ToolCallingChatOptions.builder().build();
		List<Message> messages = List.of(mock(UserMessage.class));
		SystemMessage systemMessage = new SystemMessage("Instructions {key}");
		UserMessage userMessage = new UserMessage("Question {key}", mock(Media.class));
		Map<String, Object> systemParams = Map.of("key", "value");
		Map<String, Object> userParams = Map.of("key", "value");
		List<String> toolNames = List.of("tool1", "tool2");
		ToolCallback toolCallback = mock(ToolCallback.class);
		Map<String, Object> toolContext = Map.of("key", "value");
		List<Advisor> advisors = List.of(mock(Advisor.class));
		Map<String, Object> advisorContext = Map.of("key", "value");

		AdvisedRequest advisedRequest = AdvisedRequest.builder()
			.chatModel(chatModel)
			.chatOptions(chatOptions)
			.messages(messages)
			.systemText(systemMessage.getText())
			.systemParams(systemParams)
			.userText(userMessage.getText())
			.userParams(userParams)
			.media(userMessage.getMedia())
			.functionNames(toolNames)
			.functionCallbacks(List.of(toolCallback))
			.toolContext(toolContext)
			.advisors(advisors)
			.adviseContext(advisorContext)
			.build();

		ChatClientRequest chatClientRequest = advisedRequest.toChatClientRequest();

		assertThat(chatClientRequest.context().get(ChatClientAttributes.CHAT_MODEL.getKey())).isEqualTo(chatModel);
		assertThat(chatClientRequest.prompt().getOptions()).isEqualTo(chatOptions);
		assertThat(chatClientRequest.prompt().getInstructions()).hasSize(3);
		assertThat(chatClientRequest.prompt().getInstructions().get(0)).isEqualTo(messages.get(0));
		assertThat(chatClientRequest.prompt().getInstructions().get(1).getText()).isEqualTo("Instructions value");
		assertThat(chatClientRequest.prompt().getInstructions().get(2).getText()).isEqualTo("Question value");
		assertThat(((ToolCallingChatOptions) chatClientRequest.prompt().getOptions()).getToolNames())
			.containsAll(toolNames);
		assertThat(((ToolCallingChatOptions) chatClientRequest.prompt().getOptions()).getToolCallbacks())
			.contains(toolCallback);
		assertThat(((ToolCallingChatOptions) chatClientRequest.prompt().getOptions()).getToolContext())
			.containsAllEntriesOf(toolContext);
		assertThat((List<Advisor>) chatClientRequest.context().get(ChatClientAttributes.ADVISORS.getKey()))
			.containsAll(advisors);
		assertThat(chatClientRequest.context()).containsAllEntriesOf(advisorContext);

		AdvisedRequest convertedAdvisedRequest = AdvisedRequest.from(chatClientRequest);
		assertThat(convertedAdvisedRequest.toPrompt()).isEqualTo(chatClientRequest.prompt());
		assertThat(convertedAdvisedRequest.adviseContext()).containsAllEntriesOf(chatClientRequest.context());
	}

}
