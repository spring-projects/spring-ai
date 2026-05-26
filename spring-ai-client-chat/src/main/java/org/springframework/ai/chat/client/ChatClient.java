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

import java.net.URL;
import java.nio.charset.Charset;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.api.ToolAdvisor;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationConvention;
import org.springframework.ai.chat.client.observation.ChatClientObservationConvention;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.content.Media;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.ai.template.TemplateRenderer;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;

/**
 * Client to perform stateless requests to an AI Model, using a fluent API.
 * <p>
 * Use {@link ChatClient#builder(ChatModel)} to prepare an instance.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Josh Long
 * @author Arjen Poutsma
 * @author Thomas Vitale
 * @since 1.0.0
 */
public interface ChatClient {

	static ChatClient create(ChatModel chatModel) {
		return create(chatModel, ObservationRegistry.NOOP);
	}

	static ChatClient create(ChatModel chatModel, ObservationRegistry observationRegistry) {
		return create(chatModel, observationRegistry, null, null);
	}

	static ChatClient create(ChatModel chatModel, ObservationRegistry observationRegistry,
			@Nullable ChatClientObservationConvention chatClientObservationConvention,
			@Nullable AdvisorObservationConvention advisorObservationConvention) {
		Assert.notNull(chatModel, "chatModel cannot be null");
		Assert.notNull(observationRegistry, "observationRegistry cannot be null");
		return builder(chatModel, observationRegistry, chatClientObservationConvention, advisorObservationConvention)
			.build();
	}

	static Builder builder(ChatModel chatModel) {
		return builder(chatModel, ObservationRegistry.NOOP, null, null);
	}

	static Builder builder(ChatModel chatModel, ObservationRegistry observationRegistry,
			@Nullable ChatClientObservationConvention chatClientObservationConvention,
			@Nullable AdvisorObservationConvention advisorObservationConvention) {
		return builder(chatModel, observationRegistry, chatClientObservationConvention, advisorObservationConvention,
				null);
	}

	static Builder builder(ChatModel chatModel, ObservationRegistry observationRegistry,
			@Nullable ChatClientObservationConvention chatClientObservationConvention,
			@Nullable AdvisorObservationConvention advisorObservationConvention,
			ToolCallAdvisor.@Nullable Builder<?> toolCallAdvisorBuilder) {
		Assert.notNull(chatModel, "chatModel cannot be null");
		Assert.notNull(observationRegistry, "observationRegistry cannot be null");

		return new DefaultChatClientBuilder(chatModel, observationRegistry, chatClientObservationConvention,
				advisorObservationConvention, toolCallAdvisorBuilder);
	}

	ChatClientRequestSpec prompt();

	ChatClientRequestSpec prompt(String content);

	ChatClientRequestSpec prompt(Prompt prompt);

	/**
	 * Return a {@link ChatClient.Builder} to create a new {@link ChatClient} whose
	 * settings are replicated from the default {@link ChatClientRequestSpec} of this
	 * client.
	 */
	Builder mutate();

	interface PromptUserSpec {

		PromptUserSpec text(String text);

		PromptUserSpec text(Resource text, Charset charset);

		PromptUserSpec text(Resource text);

		PromptUserSpec params(Map<String, Object> p);

		PromptUserSpec param(String k, Object v);

		PromptUserSpec media(Media... media);

		PromptUserSpec media(MimeType mimeType, URL url);

		PromptUserSpec media(MimeType mimeType, Resource resource);

		PromptUserSpec metadata(Map<String, Object> metadata);

		PromptUserSpec metadata(String k, Object v);

	}

	/**
	 * Specification for a prompt system.
	 */
	interface PromptSystemSpec {

		PromptSystemSpec text(String text);

		PromptSystemSpec text(Resource text, Charset charset);

		PromptSystemSpec text(Resource text);

		PromptSystemSpec params(Map<String, Object> p);

		PromptSystemSpec param(String k, Object v);

		PromptSystemSpec metadata(Map<String, Object> metadata);

		PromptSystemSpec metadata(String k, Object v);

	}

	interface AdvisorSpec {

		AdvisorSpec param(String k, Object v);

		AdvisorSpec params(Map<String, Object> p);

		AdvisorSpec advisors(Advisor... advisors);

		AdvisorSpec advisors(List<Advisor> advisors);

	}

	/**
	 * Configures optional behaviour for {@code entity(...)} calls. Options may be
	 * combined.
	 */
	interface EntityParamSpec {

		/**
		 * Delivers the JSON schema to the AI provider as an API-level constraint rather
		 * than appending it as prompt text. Has no effect if the underlying
		 * {@link org.springframework.ai.chat.model.ChatModel} does not support
		 * {@link org.springframework.ai.model.tool.StructuredOutputChatOptions}.
		 */
		EntityParamSpec delegateToProvider();

		/**
		 * Validates the model's JSON response against the entity schema and retries with
		 * the error feedback on failure, up to {@code maxRepeatAttempts} times (default:
		 * 3). Streaming is not supported.
		 */
		EntityParamSpec schemaValidation();

	}

	interface CallResponseSpec {

		/**
		 * Deserializes the response into a {@code T} instance, with behaviour configured
		 * via the {@code entityParamSpecConsumer}.
		 * @param type the target parameterized type
		 * @param entityParamSpecConsumer configures options such as
		 * {@link EntityParamSpec#delegateToProvider()} and
		 * {@link EntityParamSpec#schemaValidation()}
		 * @return the deserialized entity, or {@code null} if the response is empty
		 */
		<T> @Nullable T entity(ParameterizedTypeReference<T> type, Consumer<EntityParamSpec> entityParamSpecConsumer);

		/**
		 * Deserializes the response into a {@code T} instance.
		 * @param type the target parameterized type
		 * @return the deserialized entity, or {@code null} if the response is empty
		 */
		<T> @Nullable T entity(ParameterizedTypeReference<T> type);

		/**
		 * Deserializes the response using the given converter, with behaviour configured
		 * via the {@code entityParamSpecConsumer}.
		 * @param structuredOutputConverter the converter for parsing and schema
		 * resolution
		 * @param entityParamSpecConsumer configures options such as
		 * {@link EntityParamSpec#delegateToProvider()} and
		 * {@link EntityParamSpec#schemaValidation()}
		 * @return the deserialized entity, or {@code null} if the response is empty
		 */
		<T> @Nullable T entity(StructuredOutputConverter<T> structuredOutputConverter,
				Consumer<EntityParamSpec> entityParamSpecConsumer);

		/**
		 * Deserializes the response using the given converter.
		 * @param structuredOutputConverter the converter for parsing and schema
		 * resolution
		 * @return the deserialized entity, or {@code null} if the response is empty
		 */
		<T> @Nullable T entity(StructuredOutputConverter<T> structuredOutputConverter);

		/**
		 * Deserializes the response into a {@code T} instance, with behaviour configured
		 * via the {@code entityParamSpecConsumer}.
		 * @param type the target class
		 * @param entityParamSpecConsumer configures options such as
		 * {@link EntityParamSpec#delegateToProvider()} and
		 * {@link EntityParamSpec#schemaValidation()}
		 * @return the deserialized entity, or {@code null} if the response is empty
		 */
		<T> @Nullable T entity(Class<T> type, Consumer<EntityParamSpec> entityParamSpecConsumer);

		/**
		 * Deserializes the response into a {@code T} instance.
		 * @param type the target class
		 * @return the deserialized entity, or {@code null} if the response is empty
		 */
		<T> @Nullable T entity(Class<T> type);

		ChatClientResponse chatClientResponse();

		@Nullable ChatResponse chatResponse();

		@Nullable String content();

		/**
		 * Returns a {@link ResponseEntity} containing both the complete
		 * {@link ChatResponse} object and a specific entity type, with behaviour
		 * configured via the {@code entityParamSpecConsumer}.
		 * @param type the target class
		 * @param entityParamSpecConsumer configures options such as
		 * {@link EntityParamSpec#delegateToProvider()} and
		 * {@link EntityParamSpec#schemaValidation()}
		 * @return the {@link ResponseEntity} containing both the complete
		 * {@link ChatResponse} object and the deserialized entity
		 */
		<T> ResponseEntity<ChatResponse, T> responseEntity(Class<T> type,
				Consumer<EntityParamSpec> entityParamSpecConsumer);

		/**
		 * Returns a {@link ResponseEntity} containing both the complete
		 * {@link ChatResponse} object and a specific entity type.
		 * @param type the target class
		 * @return the {@link ResponseEntity} containing both the complete
		 * {@link ChatResponse} object and the deserialized entity
		 */
		<T> ResponseEntity<ChatResponse, T> responseEntity(Class<T> type);

		/**
		 * Returns a {@link ResponseEntity} containing both the complete
		 * {@link ChatResponse} object and a specific entity type, with behaviour
		 * configured via the {@code entityParamSpecConsumer}.
		 * @param type the target parameterized type
		 * @param entityParamSpecConsumer configures options such as
		 * {@link EntityParamSpec#delegateToProvider()} and
		 * {@link EntityParamSpec#schemaValidation()}
		 * @return the {@link ResponseEntity} containing both the complete
		 * {@link ChatResponse} object and the deserialized entity
		 */
		<T> ResponseEntity<ChatResponse, T> responseEntity(ParameterizedTypeReference<T> type,
				Consumer<EntityParamSpec> entityParamSpecConsumer);

		/**
		 * Returns a {@link ResponseEntity} containing both the complete
		 * {@link ChatResponse} object and a {@link Collection} of entity types.
		 * @param type the target parameterized type
		 * @return the {@link ResponseEntity} containing both the complete
		 * {@link ChatResponse} object and the deserialized entities
		 */
		<T> ResponseEntity<ChatResponse, T> responseEntity(ParameterizedTypeReference<T> type);

		/**
		 * Returns a {@link ResponseEntity} containing both the complete
		 * {@link ChatResponse} object and an entity converted using a specified
		 * {@link StructuredOutputConverter}, with behaviour configured via the
		 * {@code entityParamSpecConsumer}.
		 * @param structuredOutputConverter the converter for parsing and schema
		 * resolution
		 * @param entityParamSpecConsumer configures options such as
		 * {@link EntityParamSpec#delegateToProvider()} and
		 * {@link EntityParamSpec#schemaValidation()}
		 * @return the {@link ResponseEntity} containing both the complete
		 * {@link ChatResponse} object and the deserialized entity
		 */
		<T> ResponseEntity<ChatResponse, T> responseEntity(StructuredOutputConverter<T> structuredOutputConverter,
				Consumer<EntityParamSpec> entityParamSpecConsumer);

		/**
		 * Returns a {@link ResponseEntity} containing both the complete
		 * {@link ChatResponse} object and an entity converted using a specified
		 * {@link StructuredOutputConverter}.
		 * @param structuredOutputConverter the converter for parsing and schema
		 * resolution
		 * @return the {@link ResponseEntity} containing both the complete
		 * {@link ChatResponse} object and the deserialized entity
		 */
		<T> ResponseEntity<ChatResponse, T> responseEntity(StructuredOutputConverter<T> structuredOutputConverter);

	}

	interface StreamResponseSpec {

		Flux<ChatClientResponse> chatClientResponse();

		Flux<ChatResponse> chatResponse();

		Flux<String> content();

	}

	interface ChatClientRequestSpec {

		/**
		 * Return a {@link ChatClient.Builder} to create a new {@link ChatClient} whose
		 * settings are replicated from this {@link ChatClientRequest}.
		 */
		Builder mutate();

		ChatClientRequestSpec advisors(Consumer<AdvisorSpec> consumer);

		ChatClientRequestSpec advisors(Advisor... advisors);

		ChatClientRequestSpec advisors(List<Advisor> advisors);

		ChatClientRequestSpec messages(Message... messages);

		ChatClientRequestSpec messages(List<Message> messages);

		<B extends ChatOptions.Builder<?>> ChatClientRequestSpec options(B customizer);

		ChatClientRequestSpec tools(Consumer<ToolSpec> consumer);

		ChatClientRequestSpec tools(Object... toolObjects);

		/**
		 * @deprecated as of 2.0.0, in favor of {@link #tools(Consumer)}. To be removed in
		 * 3.0.0.
		 */
		@Deprecated(since = "2.0.0", forRemoval = true)
		ChatClientRequestSpec toolNames(String... toolNames);

		/**
		 * @deprecated as of 2.0.0, in favor of {@link #tools(Consumer)} and
		 * {@link #tools(Consumer<ToolSpec>)} To be removed in 3.0.0.
		 */
		@Deprecated(since = "2.0.0", forRemoval = true)
		ChatClientRequestSpec toolCallbacks(ToolCallback... toolCallbacks);

		/**
		 * @deprecated as of 2.0.0, in favor of {@link #tools(Consumer)} and
		 * {@link #tools(Consumer<ToolSpec>)} To be removed in 3.0.0.
		 */
		@Deprecated(since = "2.0.0", forRemoval = true)
		ChatClientRequestSpec toolCallbacks(List<ToolCallback> toolCallbacks);

		/**
		 * @deprecated as of 2.0.0, in favor of {@link #tools(Consumer)} and
		 * {@link #tools(Consumer<ToolSpec>)} To be removed in 3.0.0.
		 */
		@Deprecated(since = "2.0.0", forRemoval = true)
		ChatClientRequestSpec toolCallbacks(ToolCallbackProvider... toolCallbackProviders);

		/**
		 * @deprecated as of 2.0.0, in favor of {@link #tools(Consumer)} and
		 * {@link #tools(Consumer<ToolSpec>)} To be removed in 3.0.0.
		 */
		@Deprecated(since = "2.0.0", forRemoval = true)
		ChatClientRequestSpec toolContext(Map<String, Object> toolContext);

		ChatClientRequestSpec system(String text);

		ChatClientRequestSpec system(Resource textResource, Charset charset);

		ChatClientRequestSpec system(Resource text);

		ChatClientRequestSpec system(Consumer<PromptSystemSpec> consumer);

		ChatClientRequestSpec user(String text);

		ChatClientRequestSpec user(Resource text, Charset charset);

		ChatClientRequestSpec user(Resource text);

		ChatClientRequestSpec user(Consumer<PromptUserSpec> consumer);

		ChatClientRequestSpec templateRenderer(TemplateRenderer templateRenderer);

		CallResponseSpec call();

		StreamResponseSpec stream();

	}

	interface ToolSpec {

		ToolSpec instances(Object... toolObjects);

		ToolSpec instances(List<Object> toolObjects);

		ToolSpec callbacks(ToolCallback... toolCallbacks);

		ToolSpec callbacks(List<ToolCallback> toolCallbacks);

		ToolSpec callbacks(ToolCallbackProvider... toolCallbackProvider);

		ToolSpec context(Map<String, Object> toolContext);

		ToolSpec context(String key, Object value);

		ToolSpec advisor(ToolAdvisor toolAdvisor);

	}

	/**
	 * A mutable builder for creating a {@link ChatClient}.
	 */
	interface Builder {

		Builder defaultAdvisors(Advisor... advisors);

		Builder defaultAdvisors(Consumer<AdvisorSpec> advisorSpecConsumer);

		Builder defaultAdvisors(List<Advisor> advisors);

		Builder defaultOptions(ChatOptions.Builder chatOptions);

		Builder defaultUser(String text);

		Builder defaultUser(Resource text, Charset charset);

		Builder defaultUser(Resource text);

		Builder defaultUser(Consumer<PromptUserSpec> userSpecConsumer);

		Builder defaultSystem(String text);

		Builder defaultSystem(Resource text, Charset charset);

		Builder defaultSystem(Resource text);

		Builder defaultSystem(Consumer<PromptSystemSpec> systemSpecConsumer);

		Builder defaultTemplateRenderer(TemplateRenderer templateRenderer);

		Builder defaultTools(Consumer<ToolSpec> consumer);

		Builder defaultTools(Object... toolObjects);

		/**
		 * @deprecated as of 2.0.0, in favor of {@link #defaultTools(Consumer)}. To be
		 * removed in 3.0.0.
		 */
		@Deprecated(since = "2.0.0", forRemoval = true)
		Builder defaultToolNames(String... toolNames);

		/**
		 * @deprecated as of 2.0.0, in favor of {@link #defaultTools(Consumer)} and
		 * {@link #defaultTools(Consumer<ToolSpec>)} To be removed in 3.0.0.
		 */
		@Deprecated(since = "2.0.0", forRemoval = true)
		Builder defaultToolCallbacks(ToolCallback... toolCallbacks);

		/**
		 * @deprecated as of 2.0.0, in favor of {@link #defaultTools(Consumer)} and
		 * {@link #defaultTools(Consumer<ToolSpec>)} To be removed in 3.0.0.
		 */
		@Deprecated(since = "2.0.0", forRemoval = true)
		Builder defaultToolCallbacks(List<ToolCallback> toolCallbacks);

		/**
		 * @deprecated as of 2.0.0, in favor of {@link #defaultTools(Consumer)} and
		 * {@link #defaultTools(Consumer<ToolSpec>)} To be removed in 3.0.0.
		 */
		@Deprecated(since = "2.0.0", forRemoval = true)
		Builder defaultToolCallbacks(ToolCallbackProvider... toolCallbackProviders);

		/**
		 * @deprecated as of 2.0.0, in favor of {@link #defaultTools(Consumer)} and
		 * {@link #defaultTools(Consumer<ToolSpec>)} To be removed in 3.0.0.
		 */
		@Deprecated(since = "2.0.0", forRemoval = true)
		Builder defaultToolContext(Map<String, Object> toolContext);

		Builder clone();

		ChatClient build();

	}

}
