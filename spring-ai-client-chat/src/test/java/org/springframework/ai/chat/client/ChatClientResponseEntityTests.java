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

package org.springframework.ai.chat.client;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.MapOutputConverter;
import org.springframework.core.ParameterizedTypeReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

/**
 * @author Christian Tzolov
 * @author Alexandros Pappas
 */
@ExtendWith(MockitoExtension.class)
public class ChatClientResponseEntityTests {

	@Mock
	ChatModel chatModel;

	@Captor
	ArgumentCaptor<Prompt> promptCaptor;

	@Test
	public void responseEntityTest() {

		ChatResponseMetadata metadata = ChatResponseMetadata.builder().keyValue("key1", "value1").build();

		var chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("""
				{"name":"John", "age":30}
				"""))), metadata);

		given(this.chatModel.call(this.promptCaptor.capture())).willReturn(chatResponse);

		ResponseEntity<ChatResponse, MyBean> responseEntity = ChatClient.builder(this.chatModel)
			.build()
			.prompt()
			.user("Tell me about John")
			.call()
			.responseEntity(MyBean.class);

		assertThat(responseEntity.getResponse()).isEqualTo(chatResponse);
		assertThat(responseEntity.getResponse().getMetadata().get("key1").toString()).isEqualTo("value1");

		assertThat(responseEntity.getEntity()).isEqualTo(new MyBean("John", 30));

		Message userMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(userMessage.getText()).contains("Tell me about John");
	}

	@Test
	public void parametrizedResponseEntityTest() {

		var chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("""
				[
					{"name":"Max", "age":10},
					{"name":"Adi", "age":13}
				]
				"""))));

		given(this.chatModel.call(this.promptCaptor.capture())).willReturn(chatResponse);

		ResponseEntity<ChatResponse, List<MyBean>> responseEntity = ChatClient.builder(this.chatModel)
			.build()
			.prompt()
			.user("Tell me about them")
			.call()
			.responseEntity(new ParameterizedTypeReference<>() {

			});

		assertThat(responseEntity.getResponse()).isEqualTo(chatResponse);
		assertThat(responseEntity.getEntity().get(0)).isEqualTo(new MyBean("Max", 10));
		assertThat(responseEntity.getEntity().get(1)).isEqualTo(new MyBean("Adi", 13));

		Message userMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(userMessage.getText()).contains("Tell me about them");
	}

	@Test
	public void customSoCResponseEntityTest() {

		var chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("""
					{"name":"Max", "age":10},
				"""))));

		given(this.chatModel.call(this.promptCaptor.capture())).willReturn(chatResponse);

		ResponseEntity<ChatResponse, Map<String, Object>> responseEntity = ChatClient.builder(this.chatModel)
			.build()
			.prompt()
			.user("Tell me about Max")
			.call()
			.responseEntity(new MapOutputConverter());

		assertThat(responseEntity.getResponse()).isEqualTo(chatResponse);
		assertThat(responseEntity.getEntity().get("name")).isEqualTo("Max");
		assertThat(responseEntity.getEntity().get("age")).isEqualTo(10);

		Message userMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(userMessage.getText()).contains("Tell me about Max");
	}

	@Test
	public void whenEmptyResponseContentThenHandleGracefully() {
		var chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage(""))));
		given(this.chatModel.call(this.promptCaptor.capture())).willReturn(chatResponse);

		assertThatThrownBy(() -> ChatClient.builder(this.chatModel)
			.build()
			.prompt()
			.user("test")
			.call()
			.responseEntity(MyBean.class)).isInstanceOf(RuntimeException.class);
	}

	@Test
	public void whenInvalidJsonResponseThenThrows() {
		var chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("invalid json content"))));
		given(this.chatModel.call(this.promptCaptor.capture())).willReturn(chatResponse);

		assertThatThrownBy(() -> ChatClient.builder(this.chatModel)
			.build()
			.prompt()
			.user("test")
			.call()
			.responseEntity(MyBean.class)).isInstanceOf(RuntimeException.class);
	}

	@Test
	public void whenParameterizedTypeWithMapThenParseCorrectly() {
		var chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("""
				{
					"key1": "value1",
					"key2": "value2",
					"key3": "value3"
				}
				"""))));

		given(this.chatModel.call(this.promptCaptor.capture())).willReturn(chatResponse);

		ResponseEntity<ChatResponse, Map<String, String>> responseEntity = ChatClient.builder(this.chatModel)
			.build()
			.prompt()
			.user("test")
			.call()
			.responseEntity(new ParameterizedTypeReference<Map<String, String>>() {
			});

		assertThat(responseEntity.getEntity()).containsEntry("key1", "value1");
		assertThat(responseEntity.getEntity()).containsEntry("key2", "value2");
		assertThat(responseEntity.getEntity()).containsEntry("key3", "value3");
	}

	@Test
	public void whenEmptyArrayResponseThenReturnEmptyList() {
		var chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("[]"))));
		given(this.chatModel.call(this.promptCaptor.capture())).willReturn(chatResponse);

		ResponseEntity<ChatResponse, List<MyBean>> responseEntity = ChatClient.builder(this.chatModel)
			.build()
			.prompt()
			.user("test")
			.call()
			.responseEntity(new ParameterizedTypeReference<List<MyBean>>() {
			});

		assertThat(responseEntity.getEntity()).isEmpty();
	}

	@Test
	public void whenBooleanPrimitiveResponseThenParseCorrectly() {
		var chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("true"))));
		given(this.chatModel.call(this.promptCaptor.capture())).willReturn(chatResponse);

		ResponseEntity<ChatResponse, Boolean> responseEntity = ChatClient.builder(this.chatModel)
			.build()
			.prompt()
			.user("Is this true?")
			.call()
			.responseEntity(Boolean.class);

		assertThat(responseEntity.getEntity()).isTrue();
	}

	@Test
	public void whenIntegerResponseThenParseCorrectly() {
		var chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("1"))));
		given(this.chatModel.call(this.promptCaptor.capture())).willReturn(chatResponse);

		ResponseEntity<ChatResponse, Integer> responseEntity = ChatClient.builder(this.chatModel)
			.build()
			.prompt()
			.user("What is the answer?")
			.call()
			.responseEntity(Integer.class);

		assertThat(responseEntity.getEntity()).isEqualTo(1);
	}

	record MyBean(String name, int age) {
	}

}
