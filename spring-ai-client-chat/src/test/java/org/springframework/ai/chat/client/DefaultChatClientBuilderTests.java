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

import java.nio.charset.Charset;

import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.core.io.ClassPathResource;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/**
 * Unit tests for {@link DefaultChatClientBuilder}.
 *
 * @author Thomas Vitale
 */
class DefaultChatClientBuilderTests {

	@Test
	void whenCloneBuilder() {
		var chatModel = mock(ChatModel.class);
		var originalBuilder = new DefaultChatClientBuilder(chatModel);
		originalBuilder.defaultSystem("first instructions");
		var clonedBuilder = (DefaultChatClientBuilder) originalBuilder.clone();
		originalBuilder.defaultSystem("second instructions");

		assertThat(clonedBuilder).isNotSameAs(originalBuilder);
		var clonedBuilderRequestSpec = (DefaultChatClient.DefaultChatClientRequestSpec) ReflectionTestUtils
			.getField(clonedBuilder, "defaultRequest");
		assertThat(clonedBuilderRequestSpec).isNotNull();
		assertThat(clonedBuilderRequestSpec.getSystemText()).isEqualTo("first instructions");
	}

	@Test
	void whenChatModelIsNullThenThrows() {
		assertThatThrownBy(() -> new DefaultChatClientBuilder(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("the org.springframework.ai.chat.model.ChatModel must be non-null");
	}

	@Test
	void whenObservationRegistryIsNullThenThrows() {
		assertThatThrownBy(() -> new DefaultChatClientBuilder(mock(ChatModel.class), null, null, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("the io.micrometer.observation.ObservationRegistry must be non-null");
	}

	@Test
	void whenAdvisorObservationConventionIsNullThenReturn() {
		var builder = new DefaultChatClientBuilder(mock(ChatModel.class), mock(ObservationRegistry.class), null, null);
		assertThat(builder).isNotNull();
	}

	@Test
	void whenUserResourceIsNullThenThrows() {
		DefaultChatClientBuilder builder = new DefaultChatClientBuilder(mock(ChatModel.class));
		assertThatThrownBy(() -> builder.defaultUser(null, Charset.defaultCharset()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null");
	}

	@Test
	void whenUserCharsetIsNullThenThrows() {
		DefaultChatClientBuilder builder = new DefaultChatClientBuilder(mock(ChatModel.class));
		assertThatThrownBy(() -> builder.defaultUser(new ClassPathResource("user-prompt.txt"), null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("charset cannot be null");
	}

	@Test
	void whenSystemResourceIsNullThenThrows() {
		DefaultChatClientBuilder builder = new DefaultChatClientBuilder(mock(ChatModel.class));
		assertThatThrownBy(() -> builder.defaultSystem(null, Charset.defaultCharset()))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("text cannot be null");
	}

	@Test
	void whenSystemCharsetIsNullThenThrows() {
		DefaultChatClientBuilder builder = new DefaultChatClientBuilder(mock(ChatModel.class));
		assertThatThrownBy(() -> builder.defaultSystem(new ClassPathResource("system-prompt.txt"), null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("charset cannot be null");
	}

	@Test
	void whenTemplateRendererIsNullThenThrows() {
		DefaultChatClientBuilder builder = new DefaultChatClientBuilder(mock(ChatModel.class));
		assertThatThrownBy(() -> builder.defaultTemplateRenderer(null)).isInstanceOf(IllegalArgumentException.class)
			.hasMessage("templateRenderer cannot be null");
	}

	@Test
	void whenCloneBuilderThenModifyingOriginalDoesNotAffectClone() {
		var chatModel = mock(ChatModel.class);
		var originalBuilder = new DefaultChatClientBuilder(chatModel);
		originalBuilder.defaultSystem("original system");
		originalBuilder.defaultUser("original user");

		var clonedBuilder = (DefaultChatClientBuilder) originalBuilder.clone();

		// Modify original
		originalBuilder.defaultSystem("modified system");
		originalBuilder.defaultUser("modified user");

		var clonedRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ReflectionTestUtils.getField(clonedBuilder,
				"defaultRequest");

		assertThat(clonedRequest.getSystemText()).isEqualTo("original system");
		assertThat(clonedRequest.getUserText()).isEqualTo("original user");
	}

	@Test
	void whenBuildChatClientThenReturnsValidInstance() {
		var chatModel = mock(ChatModel.class);
		var builder = new DefaultChatClientBuilder(chatModel);

		var chatClient = builder.build();

		assertThat(chatClient).isNotNull();
		assertThat(chatClient).isInstanceOf(DefaultChatClient.class);
	}

	@Test
	void whenOverridingSystemPromptThenLatestValueIsUsed() {
		var chatModel = mock(ChatModel.class);
		var builder = new DefaultChatClientBuilder(chatModel);

		builder.defaultSystem("first system prompt");
		builder.defaultSystem("second system prompt");

		var defaultRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ReflectionTestUtils.getField(builder,
				"defaultRequest");
		assertThat(defaultRequest.getSystemText()).isEqualTo("second system prompt");
	}

	@Test
	void whenOverridingUserPromptThenLatestValueIsUsed() {
		var chatModel = mock(ChatModel.class);
		var builder = new DefaultChatClientBuilder(chatModel);

		builder.defaultUser("first user prompt");
		builder.defaultUser("second user prompt");

		var defaultRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ReflectionTestUtils.getField(builder,
				"defaultRequest");
		assertThat(defaultRequest.getUserText()).isEqualTo("second user prompt");
	}

	@Test
	void whenDefaultUserStringSetThenAppliedToRequest() {
		var chatModel = mock(ChatModel.class);
		var builder = new DefaultChatClientBuilder(chatModel);

		builder.defaultUser("test user prompt");

		var defaultRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ReflectionTestUtils.getField(builder,
				"defaultRequest");
		assertThat(defaultRequest.getUserText()).isEqualTo("test user prompt");
	}

	@Test
	void whenDefaultSystemStringSetThenAppliedToRequest() {
		var chatModel = mock(ChatModel.class);
		var builder = new DefaultChatClientBuilder(chatModel);

		builder.defaultSystem("test system prompt");

		var defaultRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ReflectionTestUtils.getField(builder,
				"defaultRequest");
		assertThat(defaultRequest.getSystemText()).isEqualTo("test system prompt");
	}

	@Test
	void whenBuilderMethodChainingThenAllSettingsApplied() {
		var chatModel = mock(ChatModel.class);

		var builder = new DefaultChatClientBuilder(chatModel).defaultSystem("system prompt").defaultUser("user prompt");

		var defaultRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ReflectionTestUtils.getField(builder,
				"defaultRequest");

		assertThat(defaultRequest.getSystemText()).isEqualTo("system prompt");
		assertThat(defaultRequest.getUserText()).isEqualTo("user prompt");
	}

	@Test
	void whenCloneWithAllSettingsThenAllAreCopied() {
		var chatModel = mock(ChatModel.class);

		var originalBuilder = new DefaultChatClientBuilder(chatModel).defaultSystem("system prompt")
			.defaultUser("user prompt");

		var clonedBuilder = (DefaultChatClientBuilder) originalBuilder.clone();
		var clonedRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ReflectionTestUtils.getField(clonedBuilder,
				"defaultRequest");

		assertThat(clonedRequest.getSystemText()).isEqualTo("system prompt");
		assertThat(clonedRequest.getUserText()).isEqualTo("user prompt");
	}

	@Test
	void whenBuilderUsedMultipleTimesThenProducesDifferentInstances() {
		var chatModel = mock(ChatModel.class);
		var builder = new DefaultChatClientBuilder(chatModel);

		var client1 = builder.build();
		var client2 = builder.build();

		assertThat(client1).isNotSameAs(client2);
		assertThat(client1).isInstanceOf(DefaultChatClient.class);
		assertThat(client2).isInstanceOf(DefaultChatClient.class);
	}

	@Test
	void whenDefaultUserWithTemplateVariablesThenProcessed() {
		var chatModel = mock(ChatModel.class);
		var builder = new DefaultChatClientBuilder(chatModel);

		builder.defaultUser("Hello {name}, welcome to {service}!");

		var defaultRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ReflectionTestUtils.getField(builder,
				"defaultRequest");
		assertThat(defaultRequest.getUserText()).isEqualTo("Hello {name}, welcome to {service}!");
	}

	@Test
	void whenMultipleSystemSettingsThenLastOneWins() {
		var chatModel = mock(ChatModel.class);
		var builder = new DefaultChatClientBuilder(chatModel);

		builder.defaultSystem("first system message");
		builder.defaultSystem("final system message");

		var defaultRequest = (DefaultChatClient.DefaultChatClientRequestSpec) ReflectionTestUtils.getField(builder,
				"defaultRequest");
		assertThat(defaultRequest.getSystemText()).isEqualTo("final system message");
	}

}
