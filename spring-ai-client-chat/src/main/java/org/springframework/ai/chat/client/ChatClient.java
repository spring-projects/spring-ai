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

import java.net.URL;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

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
		Assert.notNull(chatModel, "chatModel cannot be null");
		Assert.notNull(observationRegistry, "observationRegistry cannot be null");
		return new DefaultChatClientBuilder(chatModel, observationRegistry, chatClientObservationConvention,
				advisorObservationConvention);
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

	interface CallResponseSpec {

		<T> @Nullable T entity(ParameterizedTypeReference<T> type);

		<T> @Nullable T entity(StructuredOutputConverter<T> structuredOutputConverter);

		<T> @Nullable T entity(Class<T> type);

		ChatClientResponse chatClientResponse();

		@Nullable ChatResponse chatResponse();

		@Nullable String content();

		<T> ResponseEntity<ChatResponse, T> responseEntity(Class<T> type);

		<T> ResponseEntity<ChatResponse, T> responseEntity(ParameterizedTypeReference<T> type);

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

		<T extends ChatOptions> ChatClientRequestSpec options(T options);

		ChatClientRequestSpec toolNames(String... toolNames);

		ChatClientRequestSpec tools(Object... toolObjects);

		ChatClientRequestSpec toolCallbacks(ToolCallback... toolCallbacks);

		ChatClientRequestSpec toolCallbacks(List<ToolCallback> toolCallbacks);

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

		Builder defaultOptions(ChatOptions chatOptions);

		Builder defaultUser(String text);

		Builder defaultUser(Resource text, Charset charset);

		Builder defaultUser(Resource text);

		Builder defaultUser(Consumer<PromptUserSpec> userSpecConsumer);

		Builder defaultSystem(String text);

		Builder defaultSystem(Resource text, Charset charset);

		Builder defaultSystem(Resource text);

		Builder defaultSystem(Consumer<PromptSystemSpec> systemSpecConsumer);

		Builder defaultTemplateRenderer(TemplateRenderer templateRenderer);

		Builder defaultToolNames(String... toolNames);

		Builder defaultTools(Object... toolObjects);

		Builder defaultToolCallbacks(ToolCallback... toolCallbacks);

		Builder defaultToolCallbacks(List<ToolCallback> toolCallbacks);

		Builder defaultToolCallbacks(ToolCallbackProvider... toolCallbackProviders);

		Builder defaultToolContext(Map<String, Object> toolContext);

		Builder clone();

		ChatClient build();

	}

}
