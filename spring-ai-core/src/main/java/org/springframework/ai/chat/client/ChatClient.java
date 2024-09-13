/*
 * Copyright 2023-2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.observation.ChatClientObservationConvention;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.ai.model.Media;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.Resource;
import org.springframework.util.MimeType;

import io.micrometer.observation.ObservationRegistry;
import reactor.core.publisher.Flux;

/**
 * Client to perform stateless requests to an AI Model, using a fluent API.
 *
 * Use {@link ChatClient#builder(ChatModel)} to prepare an instance.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Josh Long
 * @author Arjen Poutsma
 * @since 1.0.0
 */
public interface ChatClient {

	static ChatClient create(ChatModel chatModel) {
		return create(chatModel, ObservationRegistry.NOOP);
	}

	static ChatClient create(ChatModel chatModel, ObservationRegistry observationRegistry) {
		return create(chatModel, observationRegistry, null);
	}

	static ChatClient create(ChatModel chatModel, ObservationRegistry observationRegistry,
			ChatClientObservationConvention observationConvention) {
		return builder(chatModel, observationRegistry, observationConvention).build();
	}

	static Builder builder(ChatModel chatModel) {
		return builder(chatModel, ObservationRegistry.NOOP, null);
	}

	static Builder builder(ChatModel chatModel, ObservationRegistry observationRegistry,
			ChatClientObservationConvention customObservationConvention) {
		return new DefaultChatClientBuilder(chatModel, observationRegistry, customObservationConvention);
	}

	ChatClientRequestSpec prompt();

	ChatClientPromptRequestSpec prompt(Prompt prompt);

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

	}

	interface PromptSystemSpec {

		PromptSystemSpec text(String text);

		PromptSystemSpec text(Resource text, Charset charset);

		PromptSystemSpec text(Resource text);

		PromptSystemSpec params(Map<String, Object> p);

		PromptSystemSpec param(String k, Object v);

	}

	interface AdvisorSpec {

		AdvisorSpec param(String k, Object v);

		AdvisorSpec params(Map<String, Object> p);

		AdvisorSpec advisors(Advisor... advisors);

		AdvisorSpec advisors(List<Advisor> advisors);

	}

	interface CallResponseSpec {

		<T> T entity(ParameterizedTypeReference<T> type);

		<T> T entity(StructuredOutputConverter<T> structuredOutputConverter);

		<T> T entity(Class<T> type);

		ChatResponse chatResponse();

		String content();

		<T> ResponseEntity<ChatResponse, T> responseEntity(Class<T> type);

		<T> ResponseEntity<ChatResponse, T> responseEntity(ParameterizedTypeReference<T> type);

		<T> ResponseEntity<ChatResponse, T> responseEntity(StructuredOutputConverter<T> structuredOutputConverter);

	}

	interface StreamResponseSpec {

		Flux<ChatResponse> chatResponse();

		Flux<String> content();

	}

	interface ChatClientPromptRequestSpec {

		CallPromptResponseSpec call();

		StreamPromptResponseSpec stream();

	}

	interface CallPromptResponseSpec {

		String content();

		List<String> contents();

		ChatResponse chatResponse();

	}

	interface StreamPromptResponseSpec {

		Flux<ChatResponse> chatResponse();

		Flux<String> content();

	}

	interface ChatClientRequestSpec {

		/**
		 * Return a {@code ChatClient.Builder} to create a new {@code ChatClient} whose
		 * settings are replicated from this {@code ChatClientRequest}.
		 */
		Builder mutate();

		ChatClientRequestSpec advisors(Consumer<AdvisorSpec> consumer);

		ChatClientRequestSpec advisors(Advisor... advisors);

		ChatClientRequestSpec advisors(List<Advisor> advisors);

		ChatClientRequestSpec messages(Message... messages);

		ChatClientRequestSpec messages(List<Message> messages);

		<T extends ChatOptions> ChatClientRequestSpec options(T options);

		<I, O> ChatClientRequestSpec function(String name, String description,
				java.util.function.Function<I, O> function);

		<I, O> ChatClientRequestSpec function(String name, String description, Class<I> inputType,
				java.util.function.Function<I, O> function);

		ChatClientRequestSpec functions(String... functionBeanNames);

		ChatClientRequestSpec system(String text);

		ChatClientRequestSpec system(Resource textResource, Charset charset);

		ChatClientRequestSpec system(Resource text);

		ChatClientRequestSpec system(Consumer<PromptSystemSpec> consumer);

		ChatClientRequestSpec user(String text);

		ChatClientRequestSpec user(Resource text, Charset charset);

		ChatClientRequestSpec user(Resource text);

		ChatClientRequestSpec user(Consumer<PromptUserSpec> consumer);

		CallResponseSpec call();

		StreamResponseSpec stream();

	}

	/**
	 * A mutable builder for creating a {@link ChatClient}.
	 */
	interface Builder {

		Builder defaultAdvisors(Advisor... advisor);

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

		<I, O> Builder defaultFunction(String name, String description, java.util.function.Function<I, O> function);

		Builder defaultFunctions(String... functionNames);

		ChatClient build();

	}

}
