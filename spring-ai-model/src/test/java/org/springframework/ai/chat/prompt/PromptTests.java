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

package org.springframework.ai.chat.prompt;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.UserMessage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link Prompt}.
 *
 * @author Thomas Vitale
 */
class PromptTests {

	@Test
	void whenContentIsNullThenThrow() {
		assertThatThrownBy(() -> new Prompt((String) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Content must not be null for SYSTEM or USER messages");

		assertThatThrownBy(() -> new Prompt((String) null, ChatOptions.builder().build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Content must not be null for SYSTEM or USER messages");
	}

	@Test
	void whenContentIsEmptyThenReturn() {
		Prompt prompt = new Prompt("");
		assertThat(prompt).isNotNull();

		prompt = new Prompt("", ChatOptions.builder().build());
		assertThat(prompt).isNotNull();
	}

	@Test
	void whenMessageIsNullThenThrow() {
		assertThatThrownBy(() -> new Prompt((Message) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("messages cannot contain null elements");

		assertThatThrownBy(() -> new Prompt((Message) null, ChatOptions.builder().build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("messages cannot contain null elements");
	}

	@Test
	void whenMessageListIsNullThenThrow() {
		assertThatThrownBy(() -> new Prompt((List<Message>) null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("messages cannot be null");

		assertThatThrownBy(() -> new Prompt((List<Message>) null, ChatOptions.builder().build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("messages cannot be null");
	}

	@Test
	void whenMessageArrayIsNullThenThrow() {
		assertThatThrownBy(() -> new Prompt((Message[]) null)).isInstanceOf(NullPointerException.class);
	}

	@Test
	void getUserMessageWhenSingle() {
		Prompt prompt = Prompt.builder().messages(new UserMessage("Hello")).build();

		assertThat(prompt.getUserMessage()).isNotNull();
		assertThat(prompt.getUserMessage().getText()).isEqualTo("Hello");
	}

	@Test
	void getUserMessageWhenMultiple() {
		Prompt prompt = Prompt.builder().messages(new UserMessage("Hello"), new UserMessage("How are you?")).build();

		assertThat(prompt.getUserMessage()).isNotNull();
		assertThat(prompt.getUserMessage().getText()).isEqualTo("How are you?");
	}

	@Test
	void getUserMessageWhenNone() {
		Prompt prompt = Prompt.builder().messages(new SystemMessage("You'll be back!")).build();

		assertThat(prompt.getUserMessage()).isNotNull();
		assertThat(prompt.getUserMessage().getText()).isEqualTo("");

		prompt = Prompt.builder().messages(List.of()).build();

		assertThat(prompt.getUserMessage()).isNotNull();
		assertThat(prompt.getUserMessage().getText()).isEqualTo("");
	}

	@Test
	void getUserMessagesWhenSingle() {
		Prompt prompt = Prompt.builder().messages(new UserMessage("Hello")).build();

		assertThat(prompt.getUserMessages()).hasSize(1);
		assertThat(prompt.getUserMessages().get(0).getText()).isEqualTo("Hello");
	}

	@Test
	void getUserMessagesWhenMultiple() {
		Prompt prompt = Prompt.builder()
			.messages(new UserMessage("Hello"), new SystemMessage("System"), new UserMessage("How are you?"))
			.build();

		assertThat(prompt.getUserMessages()).hasSize(2);
		assertThat(prompt.getUserMessages().get(0).getText()).isEqualTo("Hello");
		assertThat(prompt.getUserMessages().get(1).getText()).isEqualTo("How are you?");
	}

	@Test
	void getUserMessagesWhenNone() {
		Prompt prompt = Prompt.builder().messages(new SystemMessage("You'll be back!")).build();

		assertThat(prompt.getUserMessages()).isEmpty();

		prompt = Prompt.builder().messages(List.of()).build();

		assertThat(prompt.getUserMessages()).isEmpty();
	}

	@Test
	void getUserMessagesWithMixedMessageTypes() {
		ToolResponseMessage toolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("toolId", "toolName", "result")))
			.build();

		Prompt prompt = Prompt.builder()
			.messages(new SystemMessage("System instruction"), new UserMessage("First question"),
					new AssistantMessage("AI response"), new UserMessage("Second question"), toolResponse,
					new UserMessage("Third question"))
			.build();

		assertThat(prompt.getUserMessages()).hasSize(3);
		assertThat(prompt.getUserMessages().get(0).getText()).isEqualTo("First question");
		assertThat(prompt.getUserMessages().get(1).getText()).isEqualTo("Second question");
		assertThat(prompt.getUserMessages().get(2).getText()).isEqualTo("Third question");
	}

	@Test
	void augmentUserMessageWhenSingle() {
		Prompt prompt = Prompt.builder().messages(new UserMessage("Hello")).build();

		assertThat(prompt.getUserMessage()).isNotNull();
		assertThat(prompt.getUserMessage().getText()).isEqualTo("Hello");

		Prompt copy = prompt.augmentUserMessage(message -> message.mutate().text("How are you?").build());

		assertThat(copy.getUserMessage()).isNotNull();
		assertThat(copy.getUserMessage().getText()).isEqualTo("How are you?");
		assertThat(prompt.getUserMessage()).isNotNull();
		assertThat(prompt.getUserMessage().getText()).isEqualTo("Hello");
	}

	@Test
	void augmentUserMessageWhenMultiple() {
		Prompt prompt = Prompt.builder().messages(new UserMessage("Hello"), new UserMessage("How are you?")).build();

		assertThat(prompt.getUserMessage()).isNotNull();
		assertThat(prompt.getUserMessage().getText()).isEqualTo("How are you?");

		Prompt copy = prompt.augmentUserMessage(message -> message.mutate().text("What about you?").build());

		assertThat(copy.getUserMessage()).isNotNull();
		assertThat(copy.getUserMessage().getText()).isEqualTo("What about you?");
		assertThat(prompt.getUserMessage()).isNotNull();
		assertThat(prompt.getUserMessage().getText()).isEqualTo("How are you?");
	}

	@Test
	void augmentUserMessageWhenNone() {
		Prompt prompt = Prompt.builder().messages(new SystemMessage("You'll be back!")).build();

		assertThat(prompt.getUserMessage()).isNotNull();
		assertThat(prompt.getUserMessage().getText()).isEqualTo("");

		Prompt copy = prompt.augmentUserMessage(message -> message.mutate().text("How are you?").build());

		assertThat(copy.getInstructions().get(copy.getInstructions().size() - 1)).isInstanceOf(UserMessage.class);
		assertThat(copy.getUserMessage()).isNotNull();
		assertThat(copy.getUserMessage().getText()).isEqualTo("How are you?");
		assertThat(prompt.getUserMessage()).isNotNull();
		assertThat(prompt.getUserMessage().getText()).isEqualTo("");
	}

	@Test
	void getSystemMessageWhenSingle() {
		Prompt prompt = Prompt.builder().messages(new SystemMessage("Hello")).build();

		assertThat(prompt.getSystemMessage()).isNotNull();
		assertThat(prompt.getSystemMessage().getText()).isEqualTo("Hello");
	}

	@Test
	void getSystemMessageWhenMultiple() {
		Prompt prompt = Prompt.builder()
			.messages(new SystemMessage("Hello"), new SystemMessage("How are you?"))
			.build();

		assertThat(prompt.getSystemMessage()).isNotNull();
		assertThat(prompt.getSystemMessage().getText()).isEqualTo("Hello");
	}

	@Test
	void getSystemMessageWhenNone() {
		Prompt prompt = Prompt.builder().messages(new UserMessage("You'll be back!")).build();

		assertThat(prompt.getSystemMessage()).isNotNull();
		assertThat(prompt.getSystemMessage().getText()).isEqualTo("");

		prompt = Prompt.builder().messages(List.of()).build();

		assertThat(prompt.getSystemMessage()).isNotNull();
		assertThat(prompt.getSystemMessage().getText()).isEqualTo("");
	}

	@Test
	void augmentSystemMessageWhenSingle() {
		Prompt prompt = Prompt.builder().messages(new SystemMessage("Hello")).build();

		assertThat(prompt.getSystemMessage()).isNotNull();
		assertThat(prompt.getSystemMessage().getText()).isEqualTo("Hello");

		Prompt copy = prompt.augmentSystemMessage(message -> message.mutate().text("How are you?").build());

		assertThat(copy.getSystemMessage()).isNotNull();
		assertThat(copy.getSystemMessage().getText()).isEqualTo("How are you?");
		assertThat(prompt.getSystemMessage()).isNotNull();
		assertThat(prompt.getSystemMessage().getText()).isEqualTo("Hello");
	}

	@Test
	void augmentSystemMessageWhenMultiple() {
		Prompt prompt = Prompt.builder()
			.messages(new SystemMessage("Hello"), new SystemMessage("How are you?"))
			.build();

		assertThat(prompt.getSystemMessage()).isNotNull();
		assertThat(prompt.getSystemMessage().getText()).isEqualTo("Hello");

		Prompt copy = prompt.augmentSystemMessage(message -> message.mutate().text("What about you?").build());

		assertThat(copy.getSystemMessage()).isNotNull();
		assertThat(copy.getSystemMessage().getText()).isEqualTo("What about you?");
		assertThat(prompt.getSystemMessage()).isNotNull();
		assertThat(prompt.getSystemMessage().getText()).isEqualTo("Hello");
	}

	@Test
	void augmentSystemMessageWhenNone() {
		Prompt prompt = Prompt.builder().messages(new UserMessage("You'll be back!")).build();

		assertThat(prompt.getSystemMessage()).isNotNull();
		assertThat(prompt.getSystemMessage().getText()).isEqualTo("");

		Prompt copy = prompt.augmentSystemMessage(message -> message.mutate().text("How are you?").build());

		assertThat(copy.getInstructions().get(0)).isInstanceOf(SystemMessage.class);
		assertThat(copy.getSystemMessage()).isNotNull();
		assertThat(copy.getSystemMessage().getText()).isEqualTo("How are you?");
		assertThat(prompt.getSystemMessage()).isNotNull();
		assertThat(prompt.getSystemMessage().getText()).isEqualTo("");
	}

	@Test
	void augmentSystemMessageWhenNotFirst() {
		Message[] messages = { new UserMessage("Hi"), new SystemMessage("Hello") };
		Prompt prompt = Prompt.builder().messages(messages).build();

		assertThat(prompt.getSystemMessage()).isNotNull();
		assertThat(prompt.getUserMessage()).isNotNull();
		assertThat(prompt.getUserMessage().getText()).isEqualTo("Hi");
		assertThat(prompt.getSystemMessage().getText()).isEqualTo("Hello");

		Prompt copy = prompt.augmentSystemMessage(message -> message.mutate().text("How are you?").build());

		assertThat(copy.getSystemMessage()).isNotNull();
		assertThat(copy.getInstructions().size()).isEqualTo(messages.length);
		assertThat(copy.getSystemMessage().getText()).isEqualTo("How are you?");

		assertThat(prompt.getSystemMessage()).isNotNull();
		assertThat(prompt.getUserMessage()).isNotNull();
		assertThat(prompt.getUserMessage().getText()).isEqualTo("Hi");
		assertThat(prompt.getSystemMessage().getText()).isEqualTo("Hello");
	}

	@Test
	void shouldPreserveMessageOrder() {
		SystemMessage system = new SystemMessage("You are helpful");
		UserMessage user1 = new UserMessage("First question");
		UserMessage user2 = new UserMessage("Second question");

		Prompt prompt = Prompt.builder().messages(system, user1, user2).build();

		assertThat(prompt.getInstructions()).hasSize(3);
		assertThat(prompt.getInstructions().get(0)).isEqualTo(system);
		assertThat(prompt.getInstructions().get(1)).isEqualTo(user1);
		assertThat(prompt.getInstructions().get(2)).isEqualTo(user2);
	}

	@Test
	void shouldHandleEmptyMessageList() {
		Prompt prompt = Prompt.builder().messages(List.of()).build();

		assertThat(prompt.getInstructions()).isEmpty();
		assertThat(prompt.getUserMessage().getText()).isEmpty();
		assertThat(prompt.getSystemMessage().getText()).isEmpty();
	}

	@Test
	void shouldCreatePromptWithOptions() {
		ChatOptions options = ChatOptions.builder().model("test-model").temperature(0.5).build();
		Prompt prompt = new Prompt("Test content", options);

		assertThat(prompt.getOptions()).isEqualTo(options);
		assertThat(prompt.getUserMessage().getText()).isEqualTo("Test content");
	}

	@Test
	void shouldHandleMixedMessageTypes() {
		SystemMessage system = new SystemMessage("System message");
		UserMessage user = new UserMessage("User message");

		Prompt prompt = Prompt.builder().messages(user, system).build();

		assertThat(prompt.getInstructions()).hasSize(2);
		assertThat(prompt.getUserMessage().getText()).isEqualTo("User message");
		assertThat(prompt.getSystemMessage().getText()).isEqualTo("System message");
	}

	@Test
	void getLastUserOrToolResponseMessageWhenOnlyUserMessage() {
		Prompt prompt = Prompt.builder().messages(new UserMessage("Hello")).build();

		assertThat(prompt.getLastUserOrToolResponseMessage()).isNotNull();
		assertThat(prompt.getLastUserOrToolResponseMessage()).isInstanceOf(UserMessage.class);
		assertThat(prompt.getLastUserOrToolResponseMessage().getText()).isEqualTo("Hello");
	}

	@Test
	void getLastUserOrToolResponseMessageWhenOnlyToolResponse() {
		ToolResponseMessage toolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("toolId", "toolName", "result")))
			.build();
		Prompt prompt = Prompt.builder().messages(toolResponse).build();

		assertThat(prompt.getLastUserOrToolResponseMessage()).isNotNull();
		assertThat(prompt.getLastUserOrToolResponseMessage()).isInstanceOf(ToolResponseMessage.class);
	}

	@Test
	void getLastUserOrToolResponseMessageWhenBothPresent() {
		UserMessage userMsg = new UserMessage("User question");
		ToolResponseMessage toolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("toolId", "toolName", "result")))
			.build();

		Prompt prompt = Prompt.builder().messages(userMsg, new AssistantMessage("AI response"), toolResponse).build();

		// Should return the last one chronologically (toolResponse)
		assertThat(prompt.getLastUserOrToolResponseMessage()).isNotNull();
		assertThat(prompt.getLastUserOrToolResponseMessage()).isInstanceOf(ToolResponseMessage.class);
	}

	@Test
	void getLastUserOrToolResponseMessageWhenMultipleUserMessages() {
		Prompt prompt = Prompt.builder()
			.messages(new UserMessage("First question"), new UserMessage("Second question"))
			.build();

		// Should return the last UserMessage
		assertThat(prompt.getLastUserOrToolResponseMessage()).isNotNull();
		assertThat(prompt.getLastUserOrToolResponseMessage()).isInstanceOf(UserMessage.class);
		assertThat(prompt.getLastUserOrToolResponseMessage().getText()).isEqualTo("Second question");
	}

	@Test
	void getLastUserOrToolResponseMessageWhenOnlySystemAndAssistant() {
		Prompt prompt = Prompt.builder().messages(new SystemMessage("System"), new AssistantMessage("AI")).build();

		// Should return empty UserMessage
		assertThat(prompt.getLastUserOrToolResponseMessage()).isNotNull();
		assertThat(prompt.getLastUserOrToolResponseMessage()).isInstanceOf(UserMessage.class);
		assertThat(prompt.getLastUserOrToolResponseMessage().getText()).isEmpty();
	}

	@Test
	void getLastUserOrToolResponseMessageWhenEmpty() {
		Prompt prompt = Prompt.builder().messages(List.of()).build();

		assertThat(prompt.getLastUserOrToolResponseMessage()).isNotNull();
		assertThat(prompt.getLastUserOrToolResponseMessage()).isInstanceOf(UserMessage.class);
		assertThat(prompt.getLastUserOrToolResponseMessage().getText()).isEmpty();
	}

	@Test
	void getLastUserOrToolResponseMessageWithMixedOrdering() {
		// Test with tool response before user message
		UserMessage userMsg = new UserMessage("Latest user message");
		ToolResponseMessage toolResponse = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponseMessage.ToolResponse("toolId", "toolName", "result")))
			.build();

		Prompt prompt = Prompt.builder().messages(toolResponse, new SystemMessage("System"), userMsg).build();

		// Should return the last UserMessage
		assertThat(prompt.getLastUserOrToolResponseMessage()).isNotNull();
		assertThat(prompt.getLastUserOrToolResponseMessage()).isInstanceOf(UserMessage.class);
		assertThat(prompt.getLastUserOrToolResponseMessage().getText()).isEqualTo("Latest user message");
	}

}
