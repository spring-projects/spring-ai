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

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;

import java.util.List;

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
			.hasMessageContaining("Content must not be null for SYSTEM, DEVELOPER or USER messages");

		assertThatThrownBy(() -> new Prompt((String) null, ChatOptions.builder().build()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Content must not be null for SYSTEM, DEVELOPER or USER messages");
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
	void whenContentAndMessageAreBothDefinedThenThrow() {
		assertThatThrownBy(() -> Prompt.builder().content("Something").messages(new UserMessage("Else")).build())
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("content and messages cannot be set at the same time");
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

}
