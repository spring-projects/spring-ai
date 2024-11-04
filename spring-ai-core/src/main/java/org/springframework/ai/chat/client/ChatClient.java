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

import java.lang.reflect.Type;
import java.net.URL;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import io.micrometer.observation.ObservationRegistry;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.observation.ChatClientObservationConvention;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.ToolContext;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.converter.StructuredOutputConverter;
import org.springframework.ai.model.Media;
import org.springframework.ai.model.function.FunctionCallback;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.core.io.Resource;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;

/**
 * Client used to perform stateless requests to an AI Model, using a fluent API.
 * <p/>
 * Use {@link ChatClient#builder(ChatModel)} to prepare an instance.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Josh Long
 * @author Arjen Poutsma
 * @author Thomas Vitale
 * @author John Blum
 * @see ChatModel
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
			@Nullable ChatClientObservationConvention observationConvention) {
		Assert.notNull(chatModel, "chatModel cannot be null");
		Assert.notNull(observationRegistry, "observationRegistry cannot be null");
		return builder(chatModel, observationRegistry, observationConvention).build();
	}

	static Builder builder(ChatModel chatModel) {
		return builder(chatModel, ObservationRegistry.NOOP, null);
	}

	static Builder builder(ChatModel chatModel, ObservationRegistry observationRegistry,
			@Nullable ChatClientObservationConvention customObservationConvention) {
		Assert.notNull(chatModel, "chatModel cannot be null");
		Assert.notNull(observationRegistry, "observationRegistry cannot be null");
		return new DefaultChatClientBuilder(chatModel, observationRegistry, customObservationConvention);
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

		default PromptUserSpec text(String text) {
			Charset defaultCharset = Charset.defaultCharset();
			return text(new ByteArrayResource(text.getBytes(defaultCharset)), defaultCharset);
		}

		default PromptUserSpec text(Resource text) {
			return text(text, Charset.defaultCharset());
		}

		PromptUserSpec text(Resource text, Charset charset);

		default PromptUserSpec param(String key, Object value) {
			return params(Map.of(key, value));
		}

		PromptUserSpec params(Map<String, Object> params);

		PromptUserSpec media(Media... media);

		PromptUserSpec media(MimeType mimeType, URL url);

		PromptUserSpec media(MimeType mimeType, Resource resource);

	}

	interface PromptSystemSpec {

		default PromptSystemSpec text(String text) {
			Charset defaultCharset = Charset.defaultCharset();
			return text(new ByteArrayResource(text.getBytes(defaultCharset)), defaultCharset);
		}

		default PromptSystemSpec text(Resource text) {
			return text(text, Charset.defaultCharset());
		}

		PromptSystemSpec text(Resource text, Charset charset);

		default PromptSystemSpec param(String key, Object value) {
			return params(Map.of(key, value));
		}

		PromptSystemSpec params(Map<String, Object> params);

	}

	interface AdvisorSpec {

		default AdvisorSpec param(String key, Object value) {
			return params(Map.of(key, value));
		}

		AdvisorSpec params(Map<String, Object> params);

		default AdvisorSpec advisors(Advisor... advisors) {
			return advisors(Arrays.asList(advisors));
		}

		AdvisorSpec advisors(List<Advisor> advisors);

	}

	interface CallResponseSpec {

		@Nullable
		default <T> T entity(Class<T> type) {

			return entity(new ParameterizedTypeReference<>() {

				@Override
				public Type getType() {
					return type;
				}
			});
		}

		@Nullable
		<T> T entity(ParameterizedTypeReference<T> type);

		@Nullable
		<T> T entity(StructuredOutputConverter<T> structuredOutputConverter);

		@Nullable
		ChatResponse chatResponse();

		@Nullable
		String content();

		default <T> ResponseEntity<ChatResponse, T> responseEntity(Class<T> type) {

			return responseEntity(new ParameterizedTypeReference<T>() {

				@Override
				public Type getType() {
					return type;
				}
			});
		}

		<T> ResponseEntity<ChatResponse, T> responseEntity(ParameterizedTypeReference<T> type);

		<T> ResponseEntity<ChatResponse, T> responseEntity(StructuredOutputConverter<T> structuredOutputConverter);

	}

	interface StreamResponseSpec {

		Flux<ChatResponse> chatResponse();

		Flux<String> content();

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

		default ChatClientRequestSpec advisors(Advisor... advisors) {
			return advisors(Arrays.asList(advisors));
		}

		ChatClientRequestSpec advisors(List<Advisor> advisors);

		default ChatClientRequestSpec messages(Message... messages) {
			return messages(Arrays.asList(messages));
		}

		ChatClientRequestSpec messages(List<Message> messages);

		<T extends ChatOptions> ChatClientRequestSpec options(T options);

		<I, O> ChatClientRequestSpec function(String name, String description,
				java.util.function.Function<I, O> function);

		<I, O> ChatClientRequestSpec function(String name, String description,
				java.util.function.BiFunction<I, ToolContext, O> function);

		<I, O> ChatClientRequestSpec functions(FunctionCallback... functionCallbacks);

		<I, O> ChatClientRequestSpec function(String name, String description, Class<I> inputType,
				java.util.function.Function<I, O> function);

		ChatClientRequestSpec functions(String... functionBeanNames);

		ChatClientRequestSpec toolContext(Map<String, Object> toolContext);

		default ChatClientRequestSpec system(String text) {
			Charset defaultCharset = Charset.defaultCharset();
			return system(new ByteArrayResource(text.getBytes(defaultCharset)), defaultCharset);
		}

		default ChatClientRequestSpec system(Resource text) {
			return system(text, Charset.defaultCharset());
		}

		ChatClientRequestSpec system(Resource textResource, Charset charset);

		ChatClientRequestSpec system(Consumer<PromptSystemSpec> consumer);

		default ChatClientRequestSpec user(String text) {
			Charset defaultCharset = Charset.defaultCharset();
			return user(new ByteArrayResource(text.getBytes(defaultCharset)), defaultCharset);
		}

		default ChatClientRequestSpec user(Resource text) {
			return user(text, Charset.defaultCharset());
		}

		ChatClientRequestSpec user(Resource text, Charset charset);

		ChatClientRequestSpec user(Consumer<PromptUserSpec> consumer);

		CallResponseSpec call();

		StreamResponseSpec stream();

	}

	/**
	 * A mutable builder for creating a {@link ChatClient}.
	 */
	interface Builder {

		default Builder defaultAdvisors(Advisor... advisors) {
			return defaultAdvisors(Arrays.asList(advisors));
		}

		Builder defaultAdvisors(List<Advisor> advisors);

		Builder defaultAdvisors(Consumer<AdvisorSpec> advisorSpecConsumer);

		Builder defaultOptions(ChatOptions chatOptions);

		default Builder defaultUser(String text) {
			Charset defaulCharset = Charset.defaultCharset();
			return defaultUser(new ByteArrayResource(text.getBytes(defaulCharset)), defaulCharset);
		}

		default Builder defaultUser(Resource text) {
			return defaultUser(text, Charset.defaultCharset());
		}

		Builder defaultUser(Resource text, Charset charset);

		Builder defaultUser(Consumer<PromptUserSpec> userSpecConsumer);

		default Builder defaultSystem(String text) {
			Charset defaultCharset = Charset.defaultCharset();
			return defaultSystem(new ByteArrayResource(text.getBytes(defaultCharset)), defaultCharset);
		}

		default Builder defaultSystem(Resource text) {
			return defaultSystem(text, Charset.defaultCharset());
		}

		Builder defaultSystem(Resource text, Charset charset);

		Builder defaultSystem(Consumer<PromptSystemSpec> systemSpecConsumer);

		<I, O> Builder defaultFunction(String name, String description, java.util.function.Function<I, O> function);

		<I, O> Builder defaultFunction(String name, String description,
				java.util.function.BiFunction<I, ToolContext, O> function);

		Builder defaultFunctions(String... functionNames);

		Builder defaultFunctions(FunctionCallback... functionCallbacks);

		Builder defaultToolContext(Map<String, Object> toolContext);

		ChatClient build();

	}

}
