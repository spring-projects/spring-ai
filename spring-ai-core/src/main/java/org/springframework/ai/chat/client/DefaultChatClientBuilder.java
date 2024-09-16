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

package org.springframework.ai.chat.client;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

import org.springframework.ai.chat.client.ChatClient.Builder;
import org.springframework.ai.chat.client.ChatClient.PromptSystemSpec;
import org.springframework.ai.chat.client.ChatClient.PromptUserSpec;
import org.springframework.ai.chat.client.DefaultChatClient.DefaultChatClientRequestSpec;
import org.springframework.ai.chat.client.advisor.api.Advisor;
import org.springframework.ai.chat.client.observation.ChatClientObservationConvention;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;

import io.micrometer.observation.ObservationRegistry;

/**
 * DefaultChatClientBuilder is a builder class for creating a ChatClient.
 *
 * It provides methods to set default values for various properties of the ChatClient.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @author Josh Long
 * @author Arjen Poutsma
 * @since 1.0.0
 */
public class DefaultChatClientBuilder implements Builder {

	protected final DefaultChatClientRequestSpec defaultRequest;

	private final ChatModel chatModel;

	DefaultChatClientBuilder(ChatModel chatModel) {
		this(chatModel, ObservationRegistry.NOOP, null);
	}

	public DefaultChatClientBuilder(ChatModel chatModel, ObservationRegistry observationRegistry,
			ChatClientObservationConvention customObservationConvention) {
		Assert.notNull(chatModel, "the " + ChatModel.class.getName() + " must be non-null");
		Assert.notNull(observationRegistry, "the " + ObservationRegistry.class.getName() + " must be non-null");
		this.chatModel = chatModel;
		this.defaultRequest = new DefaultChatClientRequestSpec(chatModel, "", Map.of(), "", Map.of(), List.of(),
				List.of(), List.of(), List.of(), null, List.of(), Map.of(), observationRegistry,
				customObservationConvention);
	}

	public ChatClient build() {
		return new DefaultChatClient(this.chatModel, this.defaultRequest);
	}

	public Builder defaultAdvisors(Advisor... advisor) {
		this.defaultRequest.advisors(advisor);
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

	public Builder defaultOptions(ChatOptions chatOptions) {
		this.defaultRequest.options(chatOptions);
		return this;
	}

	public Builder defaultUser(String text) {
		this.defaultRequest.user(text);
		return this;
	}

	public Builder defaultUser(Resource text, Charset charset) {
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

	public <I, O> Builder defaultFunction(String name, String description, java.util.function.Function<I, O> function) {
		this.defaultRequest.function(name, description, function);
		return this;
	}

	public Builder defaultFunctions(String... functionNames) {
		this.defaultRequest.functions(functionNames);
		return this;
	}

}
