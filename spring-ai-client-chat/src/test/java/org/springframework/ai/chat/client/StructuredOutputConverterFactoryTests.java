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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import tools.jackson.databind.PropertyNamingStrategies;
import tools.jackson.databind.json.JsonMapper;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.BeanOutputConverter;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.ai.model.tool.StructuredOutputChatOptions;
import org.springframework.core.ParameterizedTypeReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link StructuredOutputConverterFactory} integration with
 * {@link ChatClient}.
 *
 * @author Jaehyeon Park
 */
@ExtendWith(MockitoExtension.class)
class StructuredOutputConverterFactoryTests {

	private static final String CUSTOM_NATIVE_SCHEMA = "{\"type\":\"object\"}";

	private static final String CUSTOM_VALIDATION_SCHEMA = """
			{
				"type": "object",
				"properties": {
					"value": {
						"type": "string",
						"minLength": 2
					}
				},
				"required": ["value"]
			}
			""";

	@Mock
	private ChatModel chatModel;

	@Test
	void defaultFactoryPreservesBeanOutputConverterBehavior() {
		stubResponse("{\"value\":\"default\"}");

		TestEntity entity = ChatClient.builder(this.chatModel).build().prompt().call().entity(TestEntity.class);

		assertThat(entity).isEqualTo(new TestEntity("default"));
	}

	@Test
	void defaultFactoryPreservesParameterizedBeanOutputConverterBehavior() {
		stubResponse("[{\"value\":\"default\"}]");
		var type = new ParameterizedTypeReference<List<TestEntity>>() {
		};

		List<TestEntity> entity = ChatClient.builder(this.chatModel).build().prompt().call().entity(type);

		assertThat(entity).containsExactly(new TestEntity("default"));
	}

	@Test
	void classEntityPassesParameterizedTypeReferenceToFactory() {
		stubResponse("{\"value\":\"class\"}");
		var factory = new RecordingFactory();

		TestEntity entity = client(factory).prompt().call().entity(TestEntity.class);

		assertThat(factory.targetTypes).extracting(ParameterizedTypeReference::getType)
			.containsExactly(TestEntity.class);
		assertThat(entity).isEqualTo(new TestEntity("class"));
	}

	@Test
	void parameterizedEntityPreservesCompleteGenericType() {
		stubResponse("[{\"value\":\"parameterized\"}]");
		var factory = new RecordingFactory();
		var type = new ParameterizedTypeReference<List<TestEntity>>() {
		};

		List<TestEntity> entity = client(factory).prompt().call().entity(type);

		assertThat(factory.targetTypes).extracting(ParameterizedTypeReference::getType).containsExactly(type.getType());
		assertThat(entity).containsExactly(new TestEntity("parameterized"));
	}

	@Test
	void allTypeBasedEntityAndResponseEntityOverloadsUseFactory() {
		stubResponse("{\"value\":\"class\"}");
		var factory = new RecordingFactory();
		var type = new ParameterizedTypeReference<TestEntity>() {
		};
		ChatClient chatClient = client(factory);

		chatClient.prompt().call().entity(TestEntity.class);
		chatClient.prompt().call().entity(type);
		chatClient.prompt().call().responseEntity(TestEntity.class);
		chatClient.prompt().call().responseEntity(type);

		assertThat(factory.targetTypes).extracting(ParameterizedTypeReference::getType)
			.containsExactly(TestEntity.class, type.getType(), TestEntity.class, type.getType());
	}

	@Test
	void entityParamSpecVariantsUseFactory() {
		stubResponse("{\"value\":\"class\"}");
		var factory = new RecordingFactory();
		var type = new ParameterizedTypeReference<TestEntity>() {
		};
		ChatClient chatClient = client(factory);

		chatClient.prompt().call().entity(TestEntity.class, spec -> {
		});
		chatClient.prompt().call().entity(type, spec -> {
		});
		chatClient.prompt().call().responseEntity(TestEntity.class, spec -> {
		});
		chatClient.prompt().call().responseEntity(type, spec -> {
		});

		assertThat(factory.targetTypes).extracting(ParameterizedTypeReference::getType)
			.containsExactly(TestEntity.class, type.getType(), TestEntity.class, type.getType());
	}

	@Test
	void explicitConvertersBypassFactory() {
		stubResponse("ignored");
		var expected = new TestEntity("explicit");
		var converter = new FixedConverter<>(expected);
		ChatClient chatClient = client(new FailingFactory());

		assertThat(chatClient.prompt().call().entity(converter)).isSameAs(expected);
		assertThat(chatClient.prompt().call().responseEntity(converter).getEntity()).isSameAs(expected);
		assertThat(chatClient.prompt().call().entity(converter, spec -> {
		})).isSameAs(expected);
		assertThat(chatClient.prompt().call().responseEntity(converter, spec -> {
		}).getEntity()).isSameAs(expected);
	}

	@Test
	void plainResponsesBypassFactory() {
		stubResponse("plain");
		ChatClient chatClient = client(new FailingFactory());

		assertThat(chatClient.prompt().call().content()).isEqualTo("plain");
		assertThat(chatClient.prompt().call().chatResponse()).isNotNull();
		assertThat(chatClient.prompt().call().chatClientResponse()).isNotNull();
	}

	@Test
	void configuredFactorySurvivesBuilderCloneMutationAndRequestCopy() {
		stubResponse("{\"value\":\"copied\"}");
		var factory = new RecordingFactory();
		ChatClient.Builder builder = ChatClient.builder(this.chatModel)
			.defaultStructuredOutputConverterFactory(factory);
		ChatClient client = builder.build();

		builder.clone().build().prompt().call().entity(TestEntity.class);
		client.mutate().build().prompt().call().entity(TestEntity.class);
		client.prompt().call().entity(TestEntity.class);

		assertThat(factory.targetTypes).hasSize(3);
	}

	@Test
	void replacingFactoryOnMutatedBuilderDoesNotAffectOriginalClient() {
		stubResponse("ignored");
		ChatClient original = client(new SubstitutingFactory("{\"value\":\"original\"}"));
		ChatClient mutated = original.mutate()
			.defaultStructuredOutputConverterFactory(new SubstitutingFactory("{\"value\":\"replacement\"}"))
			.build();

		assertThat(original.prompt().call().entity(TestEntity.class)).isEqualTo(new TestEntity("original"));
		assertThat(mutated.prompt().call().entity(TestEntity.class)).isEqualTo(new TestEntity("replacement"));
	}

	@Test
	void providerStructuredOutputUsesFactoryConverterSchema() {
		stubResponse("{\"value\":\"native\"}");
		when(this.chatModel.getOptions()).thenReturn(StructuredOutputChatOptions.builder().build());
		var promptCaptor = ArgumentCaptor.forClass(Prompt.class);

		client(new RecordingFactory(CUSTOM_NATIVE_SCHEMA)).prompt()
			.call()
			.entity(TestEntity.class, ChatClient.EntityParamSpec::useProviderStructuredOutput);

		verify(this.chatModel).call(promptCaptor.capture());
		var options = (StructuredOutputChatOptions) promptCaptor.getValue().getOptions();
		assertThat(options.getOutputSchema()).isEqualTo(CUSTOM_NATIVE_SCHEMA);
	}

	@Test
	void schemaValidationUsesFactoryConverterSchema() {
		stubResponses("{\"value\":\"x\"}", "{\"value\":\"validated\"}");

		TestEntity entity = client(new RecordingFactory(CUSTOM_VALIDATION_SCHEMA)).prompt()
			.call()
			.entity(TestEntity.class, ChatClient.EntityParamSpec::validateSchema);

		assertThat(entity).isEqualTo(new TestEntity("validated"));
		verify(this.chatModel, times(2)).call(any(Prompt.class));
	}

	@Test
	void unsupportedBuilderFailsExplicitlyInsteadOfIgnoringFactory() {
		ChatClient.Builder builder = mock(ChatClient.Builder.class, Answers.CALLS_REAL_METHODS);
		StructuredOutputConverterFactory factory = StructuredOutputConverterFactory
			.beanOutputConverter(JsonMapper.builder().build());

		assertThatThrownBy(() -> builder.defaultStructuredOutputConverterFactory(factory))
			.isInstanceOf(UnsupportedOperationException.class)
			.hasMessageContaining("Structured output converter factory configuration is not supported");
		assertThatThrownBy(() -> builder.defaultStructuredOutputConverterFactory(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("factory cannot be null");
	}

	@Test
	void nullFactoryAndNullFactoryResultAreRejected() {
		assertThatThrownBy(() -> ChatClient.builder(this.chatModel).defaultStructuredOutputConverterFactory(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("factory cannot be null");

		when(this.chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
		assertThatThrownBy(() -> client(new NullFactory()).prompt().call().entity(TestEntity.class))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("structuredOutputConverterFactory must not return null");
	}

	@Test
	void staticBeanOutputConverterFactoryUsesCustomMapperAndCleaner() {
		stubResponse("not json");
		JsonMapper mapper = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
		StructuredOutputConverterFactory factory = StructuredOutputConverterFactory.beanOutputConverter(mapper,
				text -> "{\"camel_value\":\"cleaned\"}");

		CamelCaseEntity entity = client(factory).prompt().call().entity(CamelCaseEntity.class);

		assertThat(entity).isEqualTo(new CamelCaseEntity("cleaned"));
	}

	@Test
	void staticBeanOutputConverterFactoryUsesCustomMapper() {
		stubResponse("{\"camel_value\":\"mapped\"}");
		JsonMapper mapper = JsonMapper.builder().propertyNamingStrategy(PropertyNamingStrategies.SNAKE_CASE).build();
		StructuredOutputConverterFactory factory = StructuredOutputConverterFactory.beanOutputConverter(mapper);

		CamelCaseEntity entity = client(factory).prompt().call().entity(CamelCaseEntity.class);

		assertThat(entity).isEqualTo(new CamelCaseEntity("mapped"));
	}

	@Test
	void staticBeanOutputConverterFactoryRejectsNullArguments() {
		JsonMapper mapper = JsonMapper.builder().build();

		assertThatThrownBy(() -> StructuredOutputConverterFactory.beanOutputConverter(null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("jsonMapper cannot be null");
		assertThatThrownBy(() -> StructuredOutputConverterFactory.beanOutputConverter(mapper, null))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessage("textCleaner cannot be null");
	}

	private ChatClient client(StructuredOutputConverterFactory factory) {
		return ChatClient.builder(this.chatModel).defaultStructuredOutputConverterFactory(factory).build();
	}

	private void stubResponse(String content) {
		when(this.chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
		when(this.chatModel.call(any(Prompt.class)))
			.thenReturn(new ChatResponse(List.of(new Generation(new AssistantMessage(content)))));
	}

	private void stubResponses(String first, String second) {
		when(this.chatModel.getOptions()).thenReturn(ChatOptions.builder().build());
		when(this.chatModel.call(any(Prompt.class))).thenReturn(response(first), response(second));
	}

	private ChatResponse response(String content) {
		return new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
	}

	private static <T> StructuredOutputConverter<T> converter(ParameterizedTypeReference<T> targetType,
			String replacementSource, String jsonSchema) {
		BeanOutputConverter<T> delegate = new BeanOutputConverter<>(targetType);
		return new StructuredOutputConverter<>() {
			@Override
			public T convert(String source) {
				return delegate.convert(replacementSource != null ? replacementSource : source);
			}

			@Override
			public String getFormat() {
				return delegate.getFormat();
			}

			@Override
			public String getJsonSchema() {
				return jsonSchema;
			}
		};
	}

	private record TestEntity(String value) {
	}

	private record CamelCaseEntity(String camelValue) {
	}

	private static final class FixedConverter<T> implements StructuredOutputConverter<T> {

		private final T value;

		private FixedConverter(T value) {
			this.value = value;
		}

		@Override
		public T convert(String source) {
			return this.value;
		}

		@Override
		public String getFormat() {
			return "test format";
		}

	}

	private static final class RecordingFactory implements StructuredOutputConverterFactory {

		private final String jsonSchema;

		private final List<ParameterizedTypeReference<?>> targetTypes = new ArrayList<>();

		private RecordingFactory() {
			this(StructuredOutputConverter.NO_JSON_SCHEMA);
		}

		private RecordingFactory(String jsonSchema) {
			this.jsonSchema = jsonSchema;
		}

		@Override
		public <T> StructuredOutputConverter<T> create(ParameterizedTypeReference<T> targetType) {
			this.targetTypes.add(targetType);
			return converter(targetType, null, this.jsonSchema);
		}

	}

	private static final class SubstitutingFactory implements StructuredOutputConverterFactory {

		private final String source;

		private SubstitutingFactory(String source) {
			this.source = source;
		}

		@Override
		public <T> StructuredOutputConverter<T> create(ParameterizedTypeReference<T> targetType) {
			return converter(targetType, this.source, StructuredOutputConverter.NO_JSON_SCHEMA);
		}

	}

	private static final class FailingFactory implements StructuredOutputConverterFactory {

		@Override
		public <T> StructuredOutputConverter<T> create(ParameterizedTypeReference<T> targetType) {
			throw new AssertionError("Factory should not be called");
		}

	}

	private static final class NullFactory implements StructuredOutputConverterFactory {

		@Override
		public <T> StructuredOutputConverter<T> create(ParameterizedTypeReference<T> targetType) {
			return null;
		}

	}

}
