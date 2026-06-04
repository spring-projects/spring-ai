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

	/**
	 * Creates a {@link Builder} for constructing a {@link ChatClient}.
	 * <p>
	 * When {@code toolCallAdvisorBuilder} is {@code null}, a default
	 * {@link org.springframework.ai.chat.client.advisor.ToolCallAdvisor} is created with
	 * a {@link org.springframework.ai.model.tool.ToolCallingManager} backed by the
	 * supplied {@code observationRegistry}.
	 * <p>
	 * When {@code toolCallAdvisorBuilder} is non-null it is used as-is. The caller is
	 * then responsible for configuring the builder's
	 * {@link org.springframework.ai.model.tool.ToolCallingManager}, including any
	 * {@link io.micrometer.observation.ObservationRegistry}, since the supplied
	 * {@code observationRegistry} will not be automatically applied to it.
	 * @param chatModel the chat model to use
	 * @param observationRegistry the observation registry for client-level observations;
	 * also used to configure the default {@code ToolCallingManager} when
	 * {@code toolCallAdvisorBuilder} is {@code null}
	 * @param chatClientObservationConvention optional custom observation convention for
	 * the chat client
	 * @param advisorObservationConvention optional custom observation convention for
	 * advisors
	 * @param toolCallAdvisorBuilder optional builder for the
	 * {@link org.springframework.ai.chat.client.advisor.ToolCallAdvisor}; when
	 * {@code null} a default is created
	 * @return a new {@link Builder}
	 */
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
		EntityParamSpec useProviderStructuredOutput();

		/**
		 * Validates the model's JSON response against the entity schema and retries with
		 * the error feedback on failure, up to {@code maxRepeatAttempts} times (default:
		 * 3). Streaming is not supported.
		 */
		EntityParamSpec validateSchema();

	}

	interface CallResponseSpec {

		/**
		 * Deserializes the response into a {@code T} instance, with behaviour configured
		 * via the {@code entityParamSpecConsumer}.
		 * @param type the target parameterized type
		 * @param entityParamSpecConsumer configures options such as
		 * {@link EntityParamSpec#useProviderStructuredOutput()} and
		 * {@link EntityParamSpec#validateSchema()}
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
		 * {@link EntityParamSpec#useProviderStructuredOutput()} and
		 * {@link EntityParamSpec#validateSchema()}
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
		 * {@link EntityParamSpec#useProviderStructuredOutput()} and
		 * {@link EntityParamSpec#validateSchema()}
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
		 * {@link EntityParamSpec#useProviderStructuredOutput()} and
		 * {@link EntityParamSpec#validateSchema()}
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
		 * {@link EntityParamSpec#useProviderStructuredOutput()} and
		 * {@link EntityParamSpec#validateSchema()}
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
		 * {@link EntityParamSpec#useProviderStructuredOutput()} and
		 * {@link EntityParamSpec#validateSchema()}
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

		/**
		 * Register one or more tools for this chat request. The method accepts a
		 * heterogeneous mix of tool representations and routes each element to the
		 * appropriate internal list automatically:
		 *
		 * <ul>
		 * <li>{@link org.springframework.ai.tool.ToolCallback} — registered directly as a
		 * callback.</li>
		 * <li>{@link org.springframework.ai.tool.ToolCallbackProvider} — registered
		 * directly as a provider; its callbacks are resolved lazily at request time.</li>
		 * <li>{@code ToolCallback[]} or {@code ToolCallbackProvider[]} — every element of
		 * the array is registered as above.</li>
		 * <li>{@link java.util.Collection} — iterated and each element is dispatched by
		 * the same rules.</li>
		 * <li>Any other object — treated as a {@code @Tool}-annotated POJO; a
		 * {@link org.springframework.ai.tool.ToolCallback} is generated for each
		 * {@link org.springframework.ai.tool.annotation.Tool}-annotated method it
		 * contains.</li>
		 * </ul>
		 *
		 * <p>
		 * Mixed calls are fully supported:
		 *
		 * <pre>{@code
		 * chatClient.prompt()
		 *     .tools(new DateTimeTools(), existingCallback, myProvider)
		 *     .toolContext(Map.of("tenantId", "acme"))
		 *     .call().content();
		 * }</pre>
		 *
		 * <p>
		 * Tools registered here are available only for this specific request. Use
		 * {@link Builder#defaultTools(Object...)} to register tools that apply to every
		 * request built from the same {@link Builder}.
		 * @param tools tool objects to register; must not be {@code null} and must not
		 * contain {@code null} elements
		 * @return this spec for chaining
		 * @throws IllegalArgumentException if {@code tools} is {@code null}, contains
		 * {@code null} elements, or if a POJO argument has no
		 * {@link org.springframework.ai.tool.annotation.Tool}-annotated methods
		 */
		ChatClientRequestSpec tools(Object... tools);

		/**
		 * @deprecated as of 2.0.0
		 */
		@Deprecated(since = "2.0.0", forRemoval = true)
		ChatClientRequestSpec toolNames(String... toolNames);

		/**
		 * @deprecated as of 2.0.0, in favor of {@link #tools(Object...)}. To be removed
		 * in 3.0.0.
		 */
		@Deprecated(since = "2.0.0", forRemoval = true)
		ChatClientRequestSpec toolCallbacks(ToolCallback... toolCallbacks);

		/**
		 * @deprecated as of 2.0.0, in favor of {@link #tools(Object...)}. To be removed
		 * in 3.0.0.
		 */
		@Deprecated(since = "2.0.0", forRemoval = true)
		ChatClientRequestSpec toolCallbacks(List<ToolCallback> toolCallbacks);

		/**
		 * @deprecated as of 2.0.0, in favor of {@link #tools(Object...)}. To be removed
		 * in 3.0.0.
		 */
		@Deprecated(since = "2.0.0", forRemoval = true)
		ChatClientRequestSpec toolCallbacks(ToolCallbackProvider... toolCallbackProviders);

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

		/**
		 * Register one or more default tools that will be available to every request
		 * built from this {@link Builder}. The method accepts the same heterogeneous mix
		 * of tool representations as {@link ChatClientRequestSpec#tools(Object...)} and
		 * applies the same automatic dispatch rules:
		 *
		 * <ul>
		 * <li>{@link org.springframework.ai.tool.ToolCallback} — registered directly as a
		 * callback.</li>
		 * <li>{@link org.springframework.ai.tool.ToolCallbackProvider} — registered
		 * directly as a provider; its callbacks are resolved lazily at request time.</li>
		 * <li>{@code ToolCallback[]} or {@code ToolCallbackProvider[]} — every element of
		 * the array is registered as above.</li>
		 * <li>{@link java.util.Collection} — iterated and each element is dispatched by
		 * the same rules.</li>
		 * <li>Any other object — treated as a {@code @Tool}-annotated POJO; a
		 * {@link org.springframework.ai.tool.ToolCallback} is generated for each
		 * {@link org.springframework.ai.tool.annotation.Tool}-annotated method it
		 * contains.</li>
		 * </ul>
		 *
		 * <p>
		 * Default tools are shared across all requests produced by {@link ChatClient}
		 * instances built from this builder. If a request also provides its own tools via
		 * {@link ChatClientRequestSpec#tools(Object...)}, those runtime tools completely
		 * override the defaults for that request.
		 *
		 * <p>
		 * WARNING: Because default tools are shared, be careful not to register tools
		 * that should only be available in specific contexts.
		 * @param tools tool objects to register; must not be {@code null} and must not
		 * contain {@code null} elements
		 * @return this builder for chaining
		 * @throws IllegalArgumentException if {@code tools} is {@code null}, contains
		 * {@code null} elements, or if a POJO argument has no
		 * {@link org.springframework.ai.tool.annotation.Tool}-annotated methods
		 */
		Builder defaultTools(Object... tools);

		/**
		 * @deprecated as of 2.0.0, in favor of {@link #defaultTools(Consumer)}.
		 */
		@Deprecated(since = "2.0.0", forRemoval = true)
		Builder defaultToolNames(String... toolNames);

		/**
		 * @deprecated as of 2.0.0, in favor of {@link #defaultTools(Object...)}. To be
		 * removed in 3.0.0.
		 */
		@Deprecated(since = "2.0.0", forRemoval = true)
		Builder defaultToolCallbacks(ToolCallback... toolCallbacks);

		/**
		 * @deprecated as of 2.0.0, in favor of {@link #defaultTools(Object...)}. To be
		 * removed in 3.0.0.
		 */
		@Deprecated(since = "2.0.0", forRemoval = true)
		Builder defaultToolCallbacks(List<ToolCallback> toolCallbacks);

		/**
		 * @deprecated as of 2.0.0, in favor of {@link #defaultTools(Object...)}. To be
		 * removed in 3.0.0.
		 */
		@Deprecated(since = "2.0.0", forRemoval = true)
		Builder defaultToolCallbacks(ToolCallbackProvider... toolCallbackProviders);

		Builder defaultToolContext(Map<String, Object> toolContext);

		Builder clone();

		ChatClient build();

	}

}
