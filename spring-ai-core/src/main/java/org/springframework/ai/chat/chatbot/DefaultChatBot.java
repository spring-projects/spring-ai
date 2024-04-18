/*
 * Copyright 2024 - 2024 the original author or authors.
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
package org.springframework.ai.chat.chatbot;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.transformer.PromptContext;
import org.springframework.ai.chat.prompt.transformer.PromptTransformer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * @author Mark Pollack
 * @author Christian Tzolov
 */
public class DefaultChatBot implements ChatBot {

	private ChatClient chatClient;

	private List<PromptTransformer> retrievers;

	private List<PromptTransformer> documentPostProcessors;

	private List<PromptTransformer> augmentors;

	private List<ChatAgentListener> chatAgentListeners;

	public DefaultChatBot(ChatClient chatClient, List<PromptTransformer> retrievers,
			List<PromptTransformer> documentPostProcessors, List<PromptTransformer> augmentors,
			List<ChatAgentListener> chatAgentListeners) {
		Objects.requireNonNull(chatClient, "chatClient must not be null");
		this.chatClient = chatClient;
		this.retrievers = retrievers;
		this.documentPostProcessors = documentPostProcessors;
		this.augmentors = augmentors;
		this.chatAgentListeners = chatAgentListeners;
	}

	public static DefaultChatAgentBuilder builder(ChatClient chatClient) {
		return new DefaultChatAgentBuilder().withChatClient(chatClient);
	}

	@Override
	public ChatBotResponse call(PromptContext promptContext) {

		PromptContext promptContextOnStart = PromptContext.from(promptContext).build();

		// Perform retrieval of documents and messages
		for (PromptTransformer retriever : this.retrievers) {
			promptContext = retriever.transform(promptContext);
		}

		// Perform post processing of all retrieved documents and messages
		for (PromptTransformer documentPostProcessor : this.documentPostProcessors) {
			promptContext = documentPostProcessor.transform(promptContext);
		}

		// Perform prompt augmentation
		for (PromptTransformer augmentor : this.augmentors) {
			promptContext = augmentor.transform(promptContext);
		}

		// Invoke Listeners onStart
		for (ChatAgentListener listener : this.chatAgentListeners) {
			listener.onStart(promptContextOnStart);
		}

		// Perform generation
		ChatResponse chatResponse = this.chatClient.call(promptContext.getPrompt());

		// Invoke Listeners onComplete
		ChatBotResponse chatBotResponse = new ChatBotResponse(promptContext, chatResponse);
		for (ChatAgentListener listener : this.chatAgentListeners) {
			listener.onComplete(chatBotResponse);
		}
		return chatBotResponse;
	}

	public static class DefaultChatAgentBuilder {

		private ChatClient chatClient;

		private List<PromptTransformer> retrievers = new ArrayList<>();

		private List<PromptTransformer> documentPostProcessors = new ArrayList<>();

		private List<PromptTransformer> augmentors = new ArrayList<>();

		private List<ChatAgentListener> chatAgentListeners = new ArrayList<>();

		public DefaultChatAgentBuilder withChatClient(ChatClient chatClient) {
			this.chatClient = chatClient;
			return this;
		}

		public DefaultChatAgentBuilder withRetrievers(List<PromptTransformer> retrievers) {
			this.retrievers = retrievers;
			return this;
		}

		public DefaultChatAgentBuilder withContentPostProcessors(List<PromptTransformer> documentPostProcessors) {
			this.documentPostProcessors = documentPostProcessors;
			return this;
		}

		public DefaultChatAgentBuilder withAugmentors(List<PromptTransformer> augmentors) {
			this.augmentors = augmentors;
			return this;
		}

		public DefaultChatAgentBuilder withChatAgentListeners(List<ChatAgentListener> chatAgentListeners) {
			this.chatAgentListeners = chatAgentListeners;
			return this;
		}

		public DefaultChatBot build() {
			return new DefaultChatBot(chatClient, retrievers, documentPostProcessors, augmentors, chatAgentListeners);
		}

	}

}