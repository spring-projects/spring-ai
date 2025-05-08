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

package org.springframework.ai.chat.client;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.content.Media;
import org.springframework.ai.model.tool.ToolCallingChatOptions;
import org.springframework.ai.template.TemplateRenderer;
import org.springframework.ai.template.st.StTemplateRenderer;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.definition.DefaultToolDefinition;
import org.springframework.ai.tool.definition.ToolDefinition;
import org.springframework.ai.tool.metadata.ToolMetadata;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link DefaultChatClientUtils}.
 *
 * @author Thomas Vitale
 */
class DefaultChatClientUtilsTests {

	@Test
	void whenInputRequestIsNullThenThrows() {
		assertThatThrownBy(() -> DefaultChatClientUtils.toChatClientRequest(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("inputRequest cannot be null");
	}

	@Test
	void whenSystemTextIsProvidedThenSystemMessageIsAddedToPrompt() {
		String systemText = "System instructions";
		ChatModel chatModel = mock(ChatModel.class);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.system(systemText);

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getInstructions()).isNotEmpty();
		assertThat(result.prompt().getInstructions().get(0)).isInstanceOf(SystemMessage.class);
		assertThat(result.prompt().getInstructions().get(0).getText()).isEqualTo(systemText);
	}

	@Test
	void whenSystemTextWithParamsIsProvidedThenSystemMessageIsRenderedAndAddedToPrompt() {
		String systemText = "System instructions for {name}";
		Map<String, Object> systemParams = Map.of("name", "Spring AI");
		ChatModel chatModel = mock(ChatModel.class);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.system(s -> s.text(systemText).params(systemParams));

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getInstructions()).isNotEmpty();
		assertThat(result.prompt().getInstructions().get(0)).isInstanceOf(SystemMessage.class);
		assertThat(result.prompt().getInstructions().get(0).getText()).isEqualTo("System instructions for Spring AI");
	}

	@Test
	void whenMessagesAreProvidedThenTheyAreAddedToPrompt() {
		List<Message> messages = List.of(new SystemMessage("System message"), new UserMessage("User message"));
		ChatModel chatModel = mock(ChatModel.class);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.messages(messages);

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getInstructions()).hasSize(2);
		assertThat(result.prompt().getInstructions().get(0).getText()).isEqualTo("System message");
		assertThat(result.prompt().getInstructions().get(1).getText()).isEqualTo("User message");
	}

	@Test
	void whenUserTextIsProvidedThenUserMessageIsAddedToPrompt() {
		String userText = "User question";
		ChatModel chatModel = mock(ChatModel.class);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.user(userText);

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getInstructions()).isNotEmpty();
		assertThat(result.prompt().getInstructions().get(0)).isInstanceOf(UserMessage.class);
		assertThat(result.prompt().getInstructions().get(0).getText()).isEqualTo(userText);
	}

	@Test
	void whenUserTextWithParamsIsProvidedThenUserMessageIsRenderedAndAddedToPrompt() {
		String userText = "Question about {topic}";
		Map<String, Object> userParams = Map.of("topic", "Spring AI");
		ChatModel chatModel = mock(ChatModel.class);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.user(s -> s.text(userText).params(userParams));

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getInstructions()).isNotEmpty();
		assertThat(result.prompt().getInstructions().get(0)).isInstanceOf(UserMessage.class);
		assertThat(result.prompt().getInstructions().get(0).getText()).isEqualTo("Question about Spring AI");
	}

	@Test
	void whenUserTextWithMediaIsProvidedThenUserMessageWithMediaIsAddedToPrompt() {
		String userText = "What's in this image?";
		Media media = mock(Media.class);
		ChatModel chatModel = mock(ChatModel.class);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.user(s -> s.text(userText).media(media));

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getInstructions()).isNotEmpty();
		assertThat(result.prompt().getInstructions().get(0)).isInstanceOf(UserMessage.class);
		UserMessage userMessage = (UserMessage) result.prompt().getInstructions().get(0);
		assertThat(userMessage.getText()).isEqualTo(userText);
		assertThat(userMessage.getMedia()).contains(media);
	}

	@Test
	void whenSystemTextAndSystemMessageAreProvidedThenSystemTextIsFirst() {
		String systemText = "System instructions";
		List<Message> messages = List.of(new SystemMessage("System message"));
		ChatModel chatModel = mock(ChatModel.class);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.system(systemText)
			.messages(messages);

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getInstructions()).hasSize(2);
		assertThat(result.prompt().getInstructions().get(0)).isInstanceOf(SystemMessage.class);
		assertThat(result.prompt().getInstructions().get(0).getText()).isEqualTo(systemText);
	}

	@Test
	void whenUserTextAndUserMessageAreProvidedThenUserTextIsLast() {
		String userText = "User question";
		List<Message> messages = List.of(new UserMessage("User message"));
		ChatModel chatModel = mock(ChatModel.class);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.user(userText)
			.messages(messages);

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getInstructions()).hasSize(2);
		assertThat(result.prompt().getInstructions()).last().isInstanceOf(UserMessage.class);
		assertThat(result.prompt().getInstructions()).last().extracting(Message::getText).isEqualTo(userText);
	}

	@Test
	void whenToolCallingChatOptionsIsProvidedThenToolNamesAreSet() {
		ToolCallingChatOptions chatOptions = ToolCallingChatOptions.builder().build();
		List<String> toolNames = List.of("tool1", "tool2");
		ChatModel chatModel = mock(ChatModel.class);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.options(chatOptions)
			.toolNames(toolNames.toArray(new String[0]));

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getOptions()).isInstanceOf(ToolCallingChatOptions.class);
		ToolCallingChatOptions resultOptions = (ToolCallingChatOptions) result.prompt().getOptions();
		assertThat(resultOptions).isNotNull();
		assertThat(resultOptions.getToolNames()).containsExactlyInAnyOrderElementsOf(toolNames);
	}

	@Test
	void whenToolCallingChatOptionsIsProvidedThenToolCallbacksAreSet() {
		ToolCallingChatOptions chatOptions = ToolCallingChatOptions.builder().build();
		ToolCallback toolCallback = new TestToolCallback("tool1");
		ChatModel chatModel = mock(ChatModel.class);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.options(chatOptions)
			.toolCallbacks(toolCallback);

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getOptions()).isInstanceOf(ToolCallingChatOptions.class);
		ToolCallingChatOptions resultOptions = (ToolCallingChatOptions) result.prompt().getOptions();
		assertThat(resultOptions).isNotNull();
		assertThat(resultOptions.getToolCallbacks()).contains(toolCallback);
	}

	@Test
	void whenToolCallingChatOptionsIsProvidedThenToolContextIsSet() {
		ToolCallingChatOptions chatOptions = ToolCallingChatOptions.builder().build();
		Map<String, Object> toolContext = Map.of("key", "value");
		ChatModel chatModel = mock(ChatModel.class);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.options(chatOptions)
			.toolContext(toolContext);

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getOptions()).isInstanceOf(ToolCallingChatOptions.class);
		ToolCallingChatOptions resultOptions = (ToolCallingChatOptions) result.prompt().getOptions();
		assertThat(resultOptions).isNotNull();
		assertThat(resultOptions.getToolContext()).containsAllEntriesOf(toolContext);
	}

	@Test
	void whenToolNamesAndChatOptionsAreProvidedThenTheToolNamesOverride() {
		Set<String> toolNames1 = Set.of("toolA", "toolB");
		ToolCallingChatOptions chatOptions = ToolCallingChatOptions.builder().toolNames(toolNames1).build();
		List<String> toolNames2 = List.of("tool1", "tool2");
		ChatModel chatModel = mock(ChatModel.class);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.options(chatOptions)
			.toolNames(toolNames2.toArray(new String[0]));

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getOptions()).isInstanceOf(ToolCallingChatOptions.class);
		ToolCallingChatOptions resultOptions = (ToolCallingChatOptions) result.prompt().getOptions();
		assertThat(resultOptions).isNotNull();
		assertThat(resultOptions.getToolNames()).containsExactlyInAnyOrderElementsOf(toolNames2);
	}

	@Test
	void whenToolCallbacksAndChatOptionsAreProvidedThenTheToolCallbacksOverride() {
		ToolCallback toolCallback1 = new TestToolCallback("tool1");
		ToolCallingChatOptions chatOptions = ToolCallingChatOptions.builder().toolCallbacks(toolCallback1).build();
		ToolCallback toolCallback2 = new TestToolCallback("tool2");
		ChatModel chatModel = mock(ChatModel.class);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.options(chatOptions)
			.toolCallbacks(toolCallback2);

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getOptions()).isInstanceOf(ToolCallingChatOptions.class);
		ToolCallingChatOptions resultOptions = (ToolCallingChatOptions) result.prompt().getOptions();
		assertThat(resultOptions).isNotNull();
		assertThat(resultOptions.getToolCallbacks()).containsExactlyInAnyOrder(toolCallback2);
	}

	@Test
	void whenToolContextAndChatOptionsAreProvidedThenTheValuesAreMerged() {
		Map<String, Object> toolContext1 = Map.of("key1", "value1");
		ToolCallingChatOptions chatOptions = ToolCallingChatOptions.builder().toolContext(toolContext1).build();
		Map<String, Object> toolContext2 = Map.of("key2", "value2");
		ChatModel chatModel = mock(ChatModel.class);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.options(chatOptions)
			.toolContext(toolContext2);

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getOptions()).isInstanceOf(ToolCallingChatOptions.class);
		ToolCallingChatOptions resultOptions = (ToolCallingChatOptions) result.prompt().getOptions();
		assertThat(resultOptions).isNotNull();
		assertThat(resultOptions.getToolContext()).containsAllEntriesOf(toolContext1)
			.containsAllEntriesOf(toolContext2);
	}

	@Test
	void whenAdvisorParamsAreProvidedThenTheyAreAddedToContext() {
		Map<String, Object> advisorParams = Map.of("key1", "value1", "key2", "value2");
		ChatModel chatModel = mock(ChatModel.class);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.advisors(a -> a.params(advisorParams));

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.context()).containsAllEntriesOf(advisorParams);
	}

	@Test
	void whenCustomTemplateRendererIsProvidedThenItIsUsedForRendering() {
		String systemText = "Instructions <name>";
		Map<String, Object> systemParams = Map.of("name", "Spring AI");
		TemplateRenderer customRenderer = StTemplateRenderer.builder()
			.startDelimiterToken('<')
			.endDelimiterToken('>')
			.build();
		ChatModel chatModel = mock(ChatModel.class);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.system(s -> s.text(systemText).params(systemParams))
			.templateRenderer(customRenderer);

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();
		assertThat(result.prompt().getInstructions()).isNotEmpty();
		assertThat(result.prompt().getInstructions().get(0)).isInstanceOf(SystemMessage.class);
		assertThat(result.prompt().getInstructions().get(0).getText()).isEqualTo("Instructions Spring AI");
	}

	@Test
	void whenAllComponentsAreProvidedThenCompleteRequestIsCreated() {
		String systemText = "System instructions for {name}";
		Map<String, Object> systemParams = Map.of("name", "Spring AI");

		String userText = "Question about {topic}";
		Map<String, Object> userParams = Map.of("topic", "Spring AI");
		Media media = mock(Media.class);

		List<Message> messages = List.of(new UserMessage("Intermediate message"));

		ToolCallingChatOptions chatOptions = ToolCallingChatOptions.builder().build();
		List<String> toolNames = List.of("tool1", "tool2");
		ToolCallback toolCallback = new TestToolCallback("tool3");
		Map<String, Object> toolContext = Map.of("toolKey", "toolValue");

		Map<String, Object> advisorParams = Map.of("advisorKey", "advisorValue");

		ChatModel chatModel = mock(ChatModel.class);
		DefaultChatClient.DefaultChatClientRequestSpec inputRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ChatClient
			.create(chatModel)
			.prompt()
			.system(s -> s.text(systemText).params(systemParams))
			.user(u -> u.text(userText).params(userParams).media(media))
			.messages(messages)
			.toolNames(toolNames.toArray(new String[0]))
			.toolCallbacks(toolCallback)
			.toolContext(toolContext)
			.options(chatOptions)
			.advisors(a -> a.params(advisorParams));

		ChatClientRequest result = DefaultChatClientUtils.toChatClientRequest(inputRequest);

		assertThat(result).isNotNull();

		assertThat(result.prompt().getInstructions()).hasSize(3);
		assertThat(result.prompt().getInstructions().get(0)).isInstanceOf(SystemMessage.class);
		assertThat(result.prompt().getInstructions().get(0).getText()).isEqualTo("System instructions for Spring AI");
		assertThat(result.prompt().getInstructions().get(1).getText()).isEqualTo("Intermediate message");
		assertThat(result.prompt().getInstructions().get(2)).isInstanceOf(UserMessage.class);
		assertThat(result.prompt().getInstructions().get(2).getText()).isEqualTo("Question about Spring AI");
		UserMessage userMessage = (UserMessage) result.prompt().getInstructions().get(2);
		assertThat(userMessage.getMedia()).contains(media);

		assertThat(result.prompt().getOptions()).isInstanceOf(ToolCallingChatOptions.class);
		ToolCallingChatOptions resultOptions = (ToolCallingChatOptions) result.prompt().getOptions();
		assertThat(resultOptions).isNotNull();
		assertThat(resultOptions.getToolNames()).containsExactlyInAnyOrderElementsOf(toolNames);
		assertThat(resultOptions.getToolCallbacks()).contains(toolCallback);
		assertThat(resultOptions.getToolContext()).containsAllEntriesOf(toolContext);

		assertThat(result.context()).containsAllEntriesOf(advisorParams);
	}

	static class TestToolCallback implements ToolCallback {

		private final ToolDefinition toolDefinition;

		private final ToolMetadata toolMetadata;

		TestToolCallback(String name) {
			this.toolDefinition = DefaultToolDefinition.builder().name(name).inputSchema("{}").build();
			this.toolMetadata = ToolMetadata.builder().build();
		}

		TestToolCallback(String name, boolean returnDirect) {
			this.toolDefinition = DefaultToolDefinition.builder().name(name).inputSchema("{}").build();
			this.toolMetadata = ToolMetadata.builder().returnDirect(returnDirect).build();
		}

		@Override
		public ToolDefinition getToolDefinition() {
			return this.toolDefinition;
		}

		@Override
		public ToolMetadata getToolMetadata() {
			return this.toolMetadata;
		}

		@Override
		public String call(String toolInput) {
			return "Mission accomplished!";
		}

	}

}
