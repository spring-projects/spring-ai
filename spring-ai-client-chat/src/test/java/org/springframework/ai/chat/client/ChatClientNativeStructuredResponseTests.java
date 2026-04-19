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

package org.springframework.ai.chat.client;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import net.javacrumbs.jsonunit.assertj.JsonAssertions;
import net.javacrumbs.jsonunit.core.Option;
import org.jspecify.annotations.NonNull;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.JsonNode;

import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisorChain;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.ai.util.json.JsonParser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.willDoNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * @author Christian Tzolov
 * @author Filip Hrisafov
 */
@ExtendWith(MockitoExtension.class)
public class ChatClientNativeStructuredResponseTests {

	// language=JSON
	private static final String USER_JSON_SCHEMA = """
			{
				"$schema": "https://json-schema.org/draft/2020-12/schema",
				"type": "object",
				"properties": {
					"age": {
						"type": "integer"
					},
					"name": {
						"type": "string"
					}
				},
				"required": [
					"age",
					"name"
				],
				"additionalProperties": false
			}
			""";

	@Mock
	ChatModel chatModel;

	@Mock
	StructuredOutputChatOptions structuredOutputChatOptions;

	@Captor
	ArgumentCaptor<Prompt> promptCaptor;

	@Test
	public void fallBackResponseEntityTest() {
		when(this.chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());

		ChatResponseMetadata metadata = ChatResponseMetadata.builder().keyValue("key1", "value1").build();

		var chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("""
				{"name":"John", "age":30}
				"""))), metadata);

		given(this.chatModel.call(this.promptCaptor.capture())).willReturn(chatResponse);

		var textCallAdvisor = new ContextCatcherCallAdvisor();
		ResponseEntity<ChatResponse, UserEntity> responseEntity = ChatClient.builder(this.chatModel)
			.build()
			.prompt()
			.advisors(textCallAdvisor)
			.user("Tell me about John")
			.call()
			.responseEntity(UserEntity.class);

		var context = textCallAdvisor.getContext();

		assertThat(context).containsKey(ChatClientAttributes.OUTPUT_FORMAT.getKey());
		assertThat(context).doesNotContainKey(ChatClientAttributes.STRUCTURED_OUTPUT_SCHEMA.getKey());
		assertThat(context).doesNotContainKey(ChatClientAttributes.STRUCTURED_OUTPUT_NATIVE.getKey());

		assertThat(responseEntity.getResponse()).isEqualTo(chatResponse);
		assertThat(responseEntity.getResponse().getMetadata().get("key1").toString()).isEqualTo("value1");

		assertThat(responseEntity.getEntity()).isEqualTo(new UserEntity("John", 30));

		Message userMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(userMessage.getText()).contains("Tell me about John", "Your response should be in JSON format");
		verify(this.structuredOutputChatOptions, never()).setOutputSchema(anyString());
	}

	@Test
	public void fallBackEntityTest() {

		when(this.chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());

		ChatResponseMetadata metadata = ChatResponseMetadata.builder().keyValue("key1", "value1").build();

		var chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("""
				{"name":"John", "age":30}
				"""))), metadata);

		given(this.chatModel.call(this.promptCaptor.capture())).willReturn(chatResponse);

		var textCallAdvisor = new ContextCatcherCallAdvisor();
		UserEntity entity = ChatClient.builder(this.chatModel)
			.build()
			.prompt()
			.advisors(textCallAdvisor)
			.user("Tell me about John")
			.call()
			.entity(UserEntity.class);

		var context = textCallAdvisor.getContext();

		assertThat(context).containsKey(ChatClientAttributes.OUTPUT_FORMAT.getKey());
		assertThat(context).doesNotContainKey(ChatClientAttributes.STRUCTURED_OUTPUT_SCHEMA.getKey());
		assertThat(context).doesNotContainKey(ChatClientAttributes.STRUCTURED_OUTPUT_NATIVE.getKey());

		assertThat(entity).isEqualTo(new UserEntity("John", 30));

		Message userMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(userMessage.getText()).contains("Tell me about John", "Your response should be in JSON format");
		verify(this.structuredOutputChatOptions, never()).setOutputSchema(anyString());
	}

	@Test
	public void nativeResponseEntityTest(@Captor ArgumentCaptor<String> outputSchemaCaptor) {
		ChatOptions.Builder builder = mock(ChatOptions.Builder.class);
		when(this.chatModel.getDefaultOptions()).thenReturn(this.structuredOutputChatOptions);
		when(this.structuredOutputChatOptions.mutate()).thenReturn(builder);
		when(builder.build()).thenReturn(this.structuredOutputChatOptions);

		ChatResponseMetadata metadata = ChatResponseMetadata.builder().keyValue("key1", "value1").build();

		var chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("""
				{"name":"John", "age":30}
				"""))), metadata);

		given(this.chatModel.call(this.promptCaptor.capture())).willReturn(chatResponse);
		willDoNothing().given(this.structuredOutputChatOptions).setOutputSchema(outputSchemaCaptor.capture());

		var textCallAdvisor = new ContextCatcherCallAdvisor();

		ResponseEntity<ChatResponse, UserEntity> responseEntity = ChatClient.builder(this.chatModel)
			.build()
			.prompt()
			.advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
			.advisors(textCallAdvisor)
			.user("Tell me about John")
			.call()
			.responseEntity(UserEntity.class);

		var context = textCallAdvisor.getContext();

		assertThat(context).containsKey(ChatClientAttributes.OUTPUT_FORMAT.getKey());
		assertThat(context).containsKey(ChatClientAttributes.STRUCTURED_OUTPUT_SCHEMA.getKey());
		assertThat(context).containsKey(ChatClientAttributes.STRUCTURED_OUTPUT_NATIVE.getKey());

		assertThat(responseEntity.getResponse()).isEqualTo(chatResponse);
		assertThat(responseEntity.getResponse().getMetadata().get("key1").toString()).isEqualTo("value1");

		assertThat(responseEntity.getEntity()).isEqualTo(new UserEntity("John", 30));

		Message userMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(userMessage.getText()).isEqualTo("Tell me about John");

		JsonAssertions.assertThatJson(outputSchemaCaptor.getValue())
			.when(Option.IGNORING_ARRAY_ORDER)
			.isEqualTo(USER_JSON_SCHEMA);
	}

	@Test
	public void nativeEntityTest(@Captor ArgumentCaptor<String> outputSchemaCaptor) {

		ChatResponseMetadata metadata = ChatResponseMetadata.builder().keyValue("key1", "value1").build();

		var chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("""
				{"name":"John", "age":30}
				"""))), metadata);

		given(this.chatModel.call(this.promptCaptor.capture())).willReturn(chatResponse);
		ChatOptions.Builder builder = mock(ChatOptions.Builder.class);
		when(this.chatModel.getDefaultOptions()).thenReturn(this.structuredOutputChatOptions);
		when(this.structuredOutputChatOptions.mutate()).thenReturn(builder);
		when(builder.build()).thenReturn(this.structuredOutputChatOptions);
		willDoNothing().given(this.structuredOutputChatOptions).setOutputSchema(outputSchemaCaptor.capture());

		var textCallAdvisor = new ContextCatcherCallAdvisor();

		UserEntity entity = ChatClient.builder(this.chatModel)
			.build()
			.prompt()
			.advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
			.advisors(textCallAdvisor)
			.user("Tell me about John")
			.call()
			.entity(UserEntity.class);

		var context = textCallAdvisor.getContext();

		assertThat(context).containsKey(ChatClientAttributes.OUTPUT_FORMAT.getKey());
		assertThat(context).containsKey(ChatClientAttributes.STRUCTURED_OUTPUT_SCHEMA.getKey());
		assertThat(context).containsKey(ChatClientAttributes.STRUCTURED_OUTPUT_NATIVE.getKey());

		assertThat(entity).isEqualTo(new UserEntity("John", 30));

		Message userMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(userMessage.getText()).isEqualTo("Tell me about John");

		JsonAssertions.assertThatJson(outputSchemaCaptor.getValue())
			.when(Option.IGNORING_ARRAY_ORDER)
			.isEqualTo(USER_JSON_SCHEMA);
	}

	@Test
	public void dynamicDisableNativeResponseEntityTest() {

		when(this.chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());

		ChatResponseMetadata metadata = ChatResponseMetadata.builder().keyValue("key1", "value1").build();

		var chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("""
				{"name":"John", "age":30}
				"""))), metadata);

		given(this.chatModel.call(this.promptCaptor.capture())).willReturn(chatResponse);

		var textCallAdvisor = new ContextCatcherCallAdvisor();

		ResponseEntity<ChatResponse, UserEntity> responseEntity = ChatClient.builder(this.chatModel)
			.build()
			.prompt()
			.advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
			.advisors(textCallAdvisor)
			.advisors(a -> a.param(ChatClientAttributes.STRUCTURED_OUTPUT_NATIVE.getKey(), false))
			.user("Tell me about John")
			.call()
			.responseEntity(UserEntity.class);

		var context = textCallAdvisor.getContext();

		assertThat(context).containsKey(ChatClientAttributes.OUTPUT_FORMAT.getKey());
		assertThat(context).doesNotContainKey(ChatClientAttributes.STRUCTURED_OUTPUT_SCHEMA.getKey());
		assertThat(context).containsEntry(ChatClientAttributes.STRUCTURED_OUTPUT_NATIVE.getKey(), false);

		assertThat(responseEntity.getResponse()).isEqualTo(chatResponse);
		assertThat(responseEntity.getResponse().getMetadata().get("key1").toString()).isEqualTo("value1");

		assertThat(responseEntity.getEntity()).isEqualTo(new UserEntity("John", 30));

		Message userMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(userMessage.getText()).contains("Tell me about John", "Your response should be in JSON format");
		verify(this.structuredOutputChatOptions, never()).setOutputSchema(anyString());
	}

	@Test
	public void dynamicDisableNativeEntityTest() {

		when(this.chatModel.getDefaultOptions()).thenReturn(ChatOptions.builder().build());

		ChatResponseMetadata metadata = ChatResponseMetadata.builder().keyValue("key1", "value1").build();

		var chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("""
				{"name":"John", "age":30}
				"""))), metadata);

		given(this.chatModel.call(this.promptCaptor.capture())).willReturn(chatResponse);

		var textCallAdvisor = new ContextCatcherCallAdvisor();

		UserEntity entity = ChatClient.builder(this.chatModel)
			.build()
			.prompt()
			.advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
			.advisors(textCallAdvisor)
			.advisors(a -> a.param(ChatClientAttributes.STRUCTURED_OUTPUT_NATIVE.getKey(), false))
			.user("Tell me about John")
			.call()
			.entity(UserEntity.class);

		var context = textCallAdvisor.getContext();

		assertThat(context).containsKey(ChatClientAttributes.OUTPUT_FORMAT.getKey());
		assertThat(context).doesNotContainKey(ChatClientAttributes.STRUCTURED_OUTPUT_SCHEMA.getKey());
		assertThat(context).containsEntry(ChatClientAttributes.STRUCTURED_OUTPUT_NATIVE.getKey(), false);

		assertThat(entity).isEqualTo(new UserEntity("John", 30));

		Message userMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(userMessage.getText()).contains("Tell me about John", "Your response should be in JSON format");
		verify(this.structuredOutputChatOptions, never()).setOutputSchema(anyString());
	}

	@Test
	public void nativeWithCustomStructuredOutputConverterResponseEntityTest(
			@Captor ArgumentCaptor<String> outputSchemaCaptor) {

		ChatResponseMetadata metadata = ChatResponseMetadata.builder().keyValue("key1", "value1").build();

		var chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("""
				{"name":"John", "age":30}
				"""))), metadata);

		given(this.chatModel.call(this.promptCaptor.capture())).willReturn(chatResponse);
		ChatOptions.Builder builder = mock(ChatOptions.Builder.class);
		when(this.chatModel.getDefaultOptions()).thenReturn(this.structuredOutputChatOptions);
		when(this.structuredOutputChatOptions.mutate()).thenReturn(builder);
		when(builder.build()).thenReturn(this.structuredOutputChatOptions);
		willDoNothing().given(this.structuredOutputChatOptions).setOutputSchema(outputSchemaCaptor.capture());

		var textCallAdvisor = new ContextCatcherCallAdvisor();

		ResponseEntity<ChatResponse, JsonNode> responseEntity = ChatClient.builder(this.chatModel)
			.build()
			.prompt()
			.advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
			.advisors(textCallAdvisor)
			.user("Tell me about John")
			.call()
			.responseEntity(new CustomJsonSchemaOutputConverter(USER_JSON_SCHEMA));

		var context = textCallAdvisor.getContext();

		assertThat(context).containsKey(ChatClientAttributes.OUTPUT_FORMAT.getKey());
		assertThat(context).containsKey(ChatClientAttributes.STRUCTURED_OUTPUT_SCHEMA.getKey());
		assertThat(context).containsKey(ChatClientAttributes.STRUCTURED_OUTPUT_NATIVE.getKey());

		assertThat(responseEntity.getResponse()).isEqualTo(chatResponse);
		assertThat(responseEntity.getResponse().getMetadata().get("key1").toString()).isEqualTo("value1");

		JsonAssertions.assertThatJson(responseEntity.getEntity()).isEqualTo("""
				{
					name: 'John',
					age: 30
				}
				""");

		Message userMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(userMessage.getText()).isEqualTo("Tell me about John");

		JsonAssertions.assertThatJson(outputSchemaCaptor.getValue())
			.when(Option.IGNORING_ARRAY_ORDER)
			.isEqualTo(USER_JSON_SCHEMA);
	}

	@Test
	public void nativeWithCustomStructuredOutputConverterEntityTest(@Captor ArgumentCaptor<String> outputSchemaCaptor) {

		ChatResponseMetadata metadata = ChatResponseMetadata.builder().keyValue("key1", "value1").build();

		var chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("""
				{"name":"John", "age":30}
				"""))), metadata);

		given(this.chatModel.call(this.promptCaptor.capture())).willReturn(chatResponse);
		ChatOptions.Builder builder = mock(ChatOptions.Builder.class);
		when(this.chatModel.getDefaultOptions()).thenReturn(this.structuredOutputChatOptions);
		when(this.structuredOutputChatOptions.mutate()).thenReturn(builder);
		when(builder.build()).thenReturn(this.structuredOutputChatOptions);
		willDoNothing().given(this.structuredOutputChatOptions).setOutputSchema(outputSchemaCaptor.capture());

		var textCallAdvisor = new ContextCatcherCallAdvisor();

		JsonNode entity = ChatClient.builder(this.chatModel)
			.build()
			.prompt()
			.advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
			.advisors(textCallAdvisor)
			.user("Tell me about John")
			.call()
			.entity(new CustomJsonSchemaOutputConverter(USER_JSON_SCHEMA));

		var context = textCallAdvisor.getContext();

		assertThat(context).containsKey(ChatClientAttributes.OUTPUT_FORMAT.getKey());
		assertThat(context).containsKey(ChatClientAttributes.STRUCTURED_OUTPUT_SCHEMA.getKey());
		assertThat(context).containsKey(ChatClientAttributes.STRUCTURED_OUTPUT_NATIVE.getKey());

		JsonAssertions.assertThatJson(entity).isEqualTo("""
				{
					name: 'John',
					age: 30
				}
				""");

		Message userMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(userMessage.getText()).isEqualTo("Tell me about John");

		JsonAssertions.assertThatJson(outputSchemaCaptor.getValue())
			.when(Option.IGNORING_ARRAY_ORDER)
			.isEqualTo(USER_JSON_SCHEMA);
	}

	@Test
	public void nativeWithCustomStructuredOutputConverterWithoutJsonSchemaResponseEntityTest() {

		ChatResponseMetadata metadata = ChatResponseMetadata.builder().keyValue("key1", "value1").build();

		var chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("""
				{"name":"John", "age":30}
				"""))), metadata);

		given(this.chatModel.call(this.promptCaptor.capture())).willReturn(chatResponse);
		ChatOptions.Builder builder = mock(ChatOptions.Builder.class);
		when(this.chatModel.getDefaultOptions()).thenReturn(this.structuredOutputChatOptions);
		when(this.structuredOutputChatOptions.mutate()).thenReturn(builder);
		when(builder.build()).thenReturn(this.structuredOutputChatOptions);

		var textCallAdvisor = new ContextCatcherCallAdvisor();

		ResponseEntity<ChatResponse, JsonNode> responseEntity = ChatClient.builder(this.chatModel)
			.build()
			.prompt()
			.advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
			.advisors(textCallAdvisor)
			.user("Tell me about John")
			.call()
			.responseEntity(new CustomJsonSchemaOutputConverter(StructuredOutputConverter.NO_JSON_SCHEMA));

		var context = textCallAdvisor.getContext();

		assertThat(context).containsKey(ChatClientAttributes.OUTPUT_FORMAT.getKey());
		assertThat(context).containsKey(ChatClientAttributes.STRUCTURED_OUTPUT_SCHEMA.getKey());
		assertThat(context).containsKey(ChatClientAttributes.STRUCTURED_OUTPUT_NATIVE.getKey());

		assertThat(responseEntity.getResponse()).isEqualTo(chatResponse);
		assertThat(responseEntity.getResponse().getMetadata().get("key1").toString()).isEqualTo("value1");

		JsonAssertions.assertThatJson(responseEntity.getEntity()).isEqualTo("""
				{
					name: 'John',
					age: 30
				}
				""");

		Message userMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(userMessage.getText()).contains("Tell me about John", "Your response should be in JSON format");

		verify(this.structuredOutputChatOptions, never()).setOutputSchema(anyString());
	}

	@Test
	public void nativeWithCustomStructuredOutputConverterWithoutJsonSchemaEntityTest() {

		ChatResponseMetadata metadata = ChatResponseMetadata.builder().keyValue("key1", "value1").build();

		var chatResponse = new ChatResponse(List.of(new Generation(new AssistantMessage("""
				{"name":"John", "age":30}
				"""))), metadata);

		given(this.chatModel.call(this.promptCaptor.capture())).willReturn(chatResponse);
		ChatOptions.Builder builder = mock(ChatOptions.Builder.class);
		when(this.chatModel.getDefaultOptions()).thenReturn(this.structuredOutputChatOptions);
		when(this.structuredOutputChatOptions.mutate()).thenReturn(builder);
		when(builder.build()).thenReturn(this.structuredOutputChatOptions);

		var textCallAdvisor = new ContextCatcherCallAdvisor();

		JsonNode entity = ChatClient.builder(this.chatModel)
			.build()
			.prompt()
			.advisors(AdvisorParams.ENABLE_NATIVE_STRUCTURED_OUTPUT)
			.advisors(textCallAdvisor)
			.user("Tell me about John")
			.call()
			.entity(new CustomJsonSchemaOutputConverter(StructuredOutputConverter.NO_JSON_SCHEMA));

		var context = textCallAdvisor.getContext();

		assertThat(context).containsKey(ChatClientAttributes.OUTPUT_FORMAT.getKey());
		assertThat(context).containsKey(ChatClientAttributes.STRUCTURED_OUTPUT_SCHEMA.getKey());
		assertThat(context).containsKey(ChatClientAttributes.STRUCTURED_OUTPUT_NATIVE.getKey());

		JsonAssertions.assertThatJson(entity).isEqualTo("""
				{
					name: 'John',
					age: 30
				}
				""");

		Message userMessage = this.promptCaptor.getValue().getInstructions().get(0);
		assertThat(userMessage.getMessageType()).isEqualTo(MessageType.USER);
		assertThat(userMessage.getText()).contains("Tell me about John", "Your response should be in JSON format");

		verify(this.structuredOutputChatOptions, never()).setOutputSchema(anyString());
	}

	record UserEntity(String name, int age) {
	}

	private static class ContextCatcherCallAdvisor implements CallAdvisor {

		private Map<String, Object> context = new ConcurrentHashMap<>();

		@Override
		public String getName() {
			return "TestAdvisor";
		}

		@Override
		public int getOrder() {
			return 0;
		}

		@Override
		public ChatClientResponse adviseCall(ChatClientRequest chatClientRequest, CallAdvisorChain callAdvisorChain) {
			var r = callAdvisorChain.nextCall(chatClientRequest);
			this.context.putAll(r.context());
			return r;
		}

		public Map<String, Object> getContext() {
			return this.context;
		}

	};

	private static final class CustomJsonSchemaOutputConverter implements StructuredOutputConverter<JsonNode> {

		private final String jsonSchema;

		private CustomJsonSchemaOutputConverter(String jsonSchema) {
			this.jsonSchema = jsonSchema;
		}

		@Override
		public @NonNull String getFormat() {
			return """
					Your response should be in JSON format.
					The JSON Schema is:
					```%s```
					""".formatted(this.jsonSchema);
		}

		@Override
		public String getJsonSchema() {
			return this.jsonSchema;
		}

		@Override
		public JsonNode convert(String source) {
			return JsonParser.getJsonMapper().readTree(source);
		}

	}

}
