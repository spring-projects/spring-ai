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

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.messages.MessageAggregator;
import org.springframework.ai.chat.prompt.transformer.PromptContext;
import org.springframework.ai.chat.prompt.transformer.PromptTransformer;

/**
 * @author Mark Pollack
 * @author Christian Tzolov
 */
public class DefaultStreamingChatBot implements StreamingChatBot {

	private StreamingChatClient streamingChatClient;

	private List<PromptTransformer> retrievers;

	private List<PromptTransformer> documentPostProcessors;

	private List<PromptTransformer> augmentors;

	private List<ChatAgentListener> chatAgentListeners;

	public DefaultStreamingChatBot(StreamingChatClient chatClient, List<PromptTransformer> retrievers,
			List<PromptTransformer> documentPostProcessors, List<PromptTransformer> augmentors,
			List<ChatAgentListener> chatAgentListeners) {
		Objects.requireNonNull(chatClient, "chatClient must not be null");
		this.streamingChatClient = chatClient;
		this.retrievers = retrievers;
		this.documentPostProcessors = documentPostProcessors;
		this.augmentors = augmentors;
		this.chatAgentListeners = chatAgentListeners;
	}

	public static DefaultChatAgentBuilder builder(StreamingChatClient chatClient) {
		return new DefaultChatAgentBuilder().withChatClient(chatClient);
	}

	@Override
	public StreamingChatBotResponse stream(PromptContext promptContext) {

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
		final var promptContext2 = promptContext;

		Flux<ChatResponse> fluxChatResponse = new MessageAggregator()
			.aggregate(this.streamingChatClient.stream(promptContext.getPrompt()), chatResponse -> {
				for (ChatAgentListener listener : this.chatAgentListeners) {
					listener.onComplete(new ChatBotResponse(promptContext2, chatResponse));
				}
			});

		// Invoke Listeners onComplete
		return new StreamingChatBotResponse(promptContext, fluxChatResponse);
	}

	public static class DefaultChatAgentBuilder {

		private StreamingChatClient chatClient;

		private List<PromptTransformer> retrievers = new ArrayList<>();

		private List<PromptTransformer> documentPostProcessors = new ArrayList<>();

		private List<PromptTransformer> augmentors = new ArrayList<>();

		private List<ChatAgentListener> chatAgentListeners = new ArrayList<>();

		public DefaultChatAgentBuilder withChatClient(StreamingChatClient chatClient) {
			this.chatClient = chatClient;
			return this;
		}

		public DefaultChatAgentBuilder withRetrievers(List<PromptTransformer> retrievers) {
			this.retrievers = retrievers;
			return this;
		}

		public DefaultChatAgentBuilder withDocumentPostProcessors(List<PromptTransformer> documentPostProcessors) {
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

		public DefaultStreamingChatBot build() {
			return new DefaultStreamingChatBot(chatClient, retrievers, documentPostProcessors, augmentors,
					chatAgentListeners);
		}

	}

}