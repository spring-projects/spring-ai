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

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.function.Consumer;

import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.client.ChatClient.Builder;
import org.springframework.ai.chat.client.ChatClient.PromptSystemSpec;
import org.springframework.ai.chat.client.ChatClient.PromptUserSpec;
import org.springframework.ai.chat.client.DefaultChatClient.DefaultChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.ToolCallAdvisor;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.advisor.observation.AdvisorObservationConvention;
import org.springframework.ai.chat.client.observation.ChatClientObservationConvention;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.model.tool.ToolCallingManager;
import org.springframework.ai.template.TemplateRenderer;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

/**
 * DefaultChatClientBuilder is a builder class for creating a ChatClient.
 * <p>
 * It provides methods to set default values for various properties of the ChatClient.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Josh Long
 * @author Arjen Poutsma
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class DefaultChatClientBuilder implements Builder {

	protected final DefaultChatClientRequestSpec defaultRequest;

	DefaultChatClientBuilder(ChatModel chatModel) {
		this(chatModel, ObservationRegistry.NOOP, null, null);
	}

	@Deprecated(since = "2.0.0", forRemoval = true)
	public DefaultChatClientBuilder(ChatModel chatModel, ObservationRegistry observationRegistry,
			@Nullable ChatClientObservationConvention chatClientObservationConvention,
			@Nullable AdvisorObservationConvention advisorObservationConvention) {
		this(chatModel, observationRegistry, chatClientObservationConvention, advisorObservationConvention, null);
	}

	/**
	 * Creates a new {@link DefaultChatClientBuilder}.
	 * <p>
	 * When {@code toolCallAdvisorBuilder} is {@code null}, a default
	 * {@link org.springframework.ai.chat.client.advisor.ToolCallAdvisor} is created with
	 * a {@link ToolCallingManager} backed by the supplied {@code observationRegistry}.
	 * <p>
	 * When {@code toolCallAdvisorBuilder} is non-null it is used as-is. The caller is
	 * then responsible for configuring the builder's {@link ToolCallingManager},
	 * including any {@link io.micrometer.observation.ObservationRegistry}, since the
	 * supplied {@code observationRegistry} will not be automatically applied to it.
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
	 */
	public DefaultChatClientBuilder(ChatModel chatModel, ObservationRegistry observationRegistry,
			@Nullable ChatClientObservationConvention chatClientObservationConvention,
			@Nullable AdvisorObservationConvention advisorObservationConvention,
			ToolCallAdvisor.@Nullable Builder<?> toolCallAdvisorBuilder) {
		Assert.notNull(chatModel, "the " + ChatModel.class.getName() + " must be non-null");
		Assert.notNull(observationRegistry, "the " + ObservationRegistry.class.getName() + " must be non-null");

		toolCallAdvisorBuilder = Objects.requireNonNullElse(toolCallAdvisorBuilder, ToolCallAdvisor.builder()
			.toolCallingManager(ToolCallingManager.builder().observationRegistry(observationRegistry).build()));

		this.defaultRequest = new DefaultChatClientRequestSpec(chatModel, null, Map.of(), Map.of(), null, Map.of(),
				Map.of(), List.of(), List.of(), List.of(), List.of(), List.of(), null, List.of(), Map.of(),
				observationRegistry, chatClientObservationConvention, Map.of(), null, advisorObservationConvention,
				toolCallAdvisorBuilder);
	}

	public ChatClient build() {
		return new DefaultChatClient(this.defaultRequest);
	}

	public Builder clone() {
		return this.defaultRequest.mutate();
	}

	public Builder defaultAdvisors(Advisor... advisors) {
		this.defaultRequest.advisors(advisors);
		return this;
	}

	public Builder defaultAdvisors(Consumer<ChatClient.AdvisorSpec> advisorSpecConsumer) {
		this.defaultRequest.advisors(advisorSpecConsumer);
		return this;
	}

	public Builder defaultAdvisors(List<Advisor> advisors) {
		this.defaultRequest.advisors(advisors);
		return this;
	}

	public Builder defaultOptions(ChatOptions.Builder customizer) {
		this.defaultRequest.options(customizer);
		return this;
	}

	public Builder defaultUser(String text) {
		this.defaultRequest.user(text);
		return this;
	}

	public Builder defaultUser(Resource text, Charset charset) {
		Assert.notNull(text, "text cannot be null");
		Assert.notNull(charset, "charset cannot be null");
		try {
			this.defaultRequest.user(text.getContentAsString(charset));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		return this;
	}

	public Builder defaultUser(Resource text) {
		return this.defaultUser(text, Charset.defaultCharset());
	}

	public Builder defaultUser(Consumer<PromptUserSpec> userSpecConsumer) {
		this.defaultRequest.user(userSpecConsumer);
		return this;
	}

	public Builder defaultSystem(String text) {
		this.defaultRequest.system(text);
		return this;
	}

	public Builder defaultSystem(Resource text, Charset charset) {
		Assert.notNull(text, "text cannot be null");
		Assert.notNull(charset, "charset cannot be null");
		try {
			this.defaultRequest.system(text.getContentAsString(charset));
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
		return this;
	}

	public Builder defaultSystem(Resource text) {
		return this.defaultSystem(text, Charset.defaultCharset());
	}

	public Builder defaultSystem(Consumer<PromptSystemSpec> systemSpecConsumer) {
		this.defaultRequest.system(systemSpecConsumer);
		return this;
	}

	/**
	 * @deprecated as of 2.0.0, in favor of {@link #defaultTools(Consumer)}. To be removed
	 * in 3.0.0.
	 */
	@Deprecated(since = "2.0.0", forRemoval = true)
	@Override
	public Builder defaultToolNames(String... toolNames) {
		this.defaultRequest.toolNames(toolNames);
		return this;
	}

	@Override
	public Builder defaultTools(Object... toolObjects) {
		this.defaultRequest.tools(toolObjects);
		return this;
	}

	/**
	 * @deprecated as of 2.0.0, in favor of {@link #defaultTools(Object...)}. To be
	 * removed in 3.0.0.
	 */
	@Deprecated(since = "2.0.0", forRemoval = true)
	@Override
	public Builder defaultToolCallbacks(ToolCallback... toolCallbacks) {
		this.defaultRequest.tools(toolCallbacks);
		return this;
	}

	/**
	 * @deprecated as of 2.0.0, in favor of {@link #defaultTools(Consumer)}. To be removed
	 * in 3.0.0.
	 */
	@Deprecated(since = "2.0.0", forRemoval = true)
	@Override
	public Builder defaultToolCallbacks(List<ToolCallback> toolCallbacks) {
		this.defaultRequest.tools(toolCallbacks);
		return this;
	}

	/**
	 * @deprecated as of 2.0.0, in favor of {@link #defaultTools(Consumer)}. To be removed
	 * in 3.0.0.
	 */
	@Deprecated(since = "2.0.0", forRemoval = true)
	@Override
	public Builder defaultToolCallbacks(ToolCallbackProvider... toolCallbackProviders) {
		this.defaultRequest.tools(toolCallbackProviders);
		return this;
	}

	public Builder defaultToolContext(Map<String, Object> toolContext) {
		this.defaultRequest.toolContext(toolContext);
		return this;
	}

	public Builder defaultTemplateRenderer(TemplateRenderer templateRenderer) {
		Assert.notNull(templateRenderer, "templateRenderer cannot be null");
		this.defaultRequest.templateRenderer(templateRenderer);
		return this;
	}

	void addMessages(List<Message> messages) {
		this.defaultRequest.messages(messages);
	}

}
