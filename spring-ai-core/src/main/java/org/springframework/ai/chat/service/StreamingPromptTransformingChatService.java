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
package org.springframework.ai.chat.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import org.springframework.ai.chat.prompt.transformer.ChatServiceContext;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.MessageAggregator;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.prompt.transformer.PromptTransformer;

/**
 * @author Mark Pollack
 * @author Christian Tzolov
 */
public class StreamingPromptTransformingChatService implements StreamingChatService {

	private StreamingChatModel streamingChatModel;

	private List<PromptTransformer> retrievers;

	private List<PromptTransformer> documentPostProcessors;

	private List<PromptTransformer> augmentors;

	private List<ChatServiceListener> chatServiceListeners;

	public StreamingPromptTransformingChatService(StreamingChatModel chatModel, List<PromptTransformer> retrievers,
			List<PromptTransformer> documentPostProcessors, List<PromptTransformer> augmentors,
			List<ChatServiceListener> chatServiceListeners) {
		Objects.requireNonNull(chatModel, "chatModel must not be null");
		this.streamingChatModel = chatModel;
		this.retrievers = retrievers;
		this.documentPostProcessors = documentPostProcessors;
		this.augmentors = augmentors;
		this.chatServiceListeners = chatServiceListeners;
	}

	public static Builder builder(StreamingChatModel chatModel) {
		return new Builder().withChatModel(chatModel);
	}

	@Override
	public StreamingChatServiceResponse stream(ChatServiceContext chatServiceContext) {

		ChatServiceContext chatServiceContextOnStart = ChatServiceContext.from(chatServiceContext).build();

		// Perform retrieval of documents and messages
		for (PromptTransformer retriever : this.retrievers) {
			chatServiceContext = retriever.transform(chatServiceContext);
		}

		// Perform post processing of all retrieved documents and messages
		for (PromptTransformer documentPostProcessor : this.documentPostProcessors) {
			chatServiceContext = documentPostProcessor.transform(chatServiceContext);
		}

		// Perform prompt augmentation
		for (PromptTransformer augmentor : this.augmentors) {
			chatServiceContext = augmentor.transform(chatServiceContext);
		}

		// Invoke Listeners onStart
		for (ChatServiceListener listener : this.chatServiceListeners) {
			listener.onStart(chatServiceContextOnStart);
		}

		// Perform generation
		final var promptContext2 = chatServiceContext;

		Flux<ChatResponse> fluxChatResponse = new MessageAggregator()
			.aggregate(this.streamingChatModel.stream(chatServiceContext.getPrompt()), chatResponse -> {
				for (ChatServiceListener listener : this.chatServiceListeners) {
					listener.onComplete(new ChatServiceResponse(promptContext2, chatResponse));
				}
			});

		// Invoke Listeners onComplete
		return new StreamingChatServiceResponse(chatServiceContext, fluxChatResponse);
	}

	public static class Builder {

		private StreamingChatModel chatModel;

		private List<PromptTransformer> retrievers = new ArrayList<>();

		private List<PromptTransformer> documentPostProcessors = new ArrayList<>();

		private List<PromptTransformer> augmentors = new ArrayList<>();

		private List<ChatServiceListener> chatServiceListeners = new ArrayList<>();

		public Builder withChatModel(StreamingChatModel chatModel) {
			this.chatModel = chatModel;
			return this;
		}

		public Builder withRetrievers(List<PromptTransformer> retrievers) {
			this.retrievers = retrievers;
			return this;
		}

		public Builder withDocumentPostProcessors(List<PromptTransformer> documentPostProcessors) {
			this.documentPostProcessors = documentPostProcessors;
			return this;
		}

		public Builder withAugmentors(List<PromptTransformer> augmentors) {
			this.augmentors = augmentors;
			return this;
		}

		public Builder withChatServiceListeners(List<ChatServiceListener> chatServiceListeners) {
			this.chatServiceListeners = chatServiceListeners;
			return this;
		}

		public StreamingPromptTransformingChatService build() {
			return new StreamingPromptTransformingChatService(chatModel, retrievers, documentPostProcessors, augmentors,
					chatServiceListeners);
		}

	}

}