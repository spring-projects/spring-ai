/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.chat;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.function.FunctionCallingOptions;
import org.springframework.util.MimeTypeUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * @author Christian Tzolov
 */
@ExtendWith(MockitoExtension.class)
public class ChatClientTest {

	@Mock
	ChatModel chatModel;

	@Captor
	ArgumentCaptor<Prompt> promptCaptor;

	@BeforeEach
	public void beforeAll() {
		when(chatModel.call(promptCaptor.capture()))
				.thenReturn(new ChatResponse(List.of(new Generation("response"))));
	}

	@Test
	public void simpleUserPrompt() {
		assertThat(ChatClient.builder(chatModel).build().prompt().user("User prompt").call().content())
			.isEqualTo("response");

		Message userMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getContent()).isEqualTo("User prompt");
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
	}

	@Test
	public void simpleUserPromptObject() throws MalformedURLException {
		UserMessage message = new UserMessage("User prompt");
		Prompt prompt = new Prompt(message);
		assertThat(ChatClient.builder(chatModel).build().prompt(prompt).call().content()).isEqualTo("response");

		Message userMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getContent()).isEqualTo("User prompt");
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
	}

	@Test
	public void simpleSystemPrompt() throws MalformedURLException {
		String response = ChatClient.builder(chatModel).build().prompt().system("System prompt").call().content();

		assertThat(response).isEqualTo("response");

		assertThat(promptCaptor.getValue().getInstructions()).hasSize(2);

		Message systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualTo("System prompt");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		// Is this expected?
		Message userMessage = promptCaptor.getValue().getInstructions().get(1);
		assertThat(userMessage.getContent()).isEqualTo("");
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
	}

	@Test
	public void complexCall() throws MalformedURLException {

		var options = FunctionCallingOptions.builder().build();
		when(chatModel.getDefaultOptions()).thenReturn(options);

		var url = new URL("https://docs.spring.io/spring-ai/reference/1.0-SNAPSHOT/_images/multimodal.test.png");

		// @formatter:off
		ChatClient client = ChatClient.builder(chatModel)
				.defaultSystem("System text")
				.defaultFunctions("function1")
				.build();

		String response = client.prompt()
				.user(u -> u.text("User text {music}").param("music", "Rock").media(MimeTypeUtils.IMAGE_PNG, url))
				.call()
				.content();
		// @formatter:on

		assertThat(response).isEqualTo("response");
		assertThat(promptCaptor.getValue().getInstructions()).hasSize(2);

		Message systemMessage = promptCaptor.getValue().getInstructions().get(0);
		assertThat(systemMessage.getContent()).isEqualTo("System text");
		assertThat(systemMessage.getMessageType()).isEqualTo(MessageType.SYSTEM);

		Message userMessage = promptCaptor.getValue().getInstructions().get(1);
		assertThat(userMessage.getContent()).isEqualTo("User text Rock");
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(userMessage.getMedia()).hasSize(1);
		assertThat(userMessage.getMedia().iterator().next().getMimeType()).isEqualTo(MimeTypeUtils.IMAGE_PNG);
		assertThat(userMessage.getMedia().iterator().next().getData())
			.isEqualTo("https://docs.spring.io/spring-ai/reference/1.0-SNAPSHOT/_images/multimodal.test.png");

		assertThat(options.getFunctions()).containsExactly("function1");
	}

}
