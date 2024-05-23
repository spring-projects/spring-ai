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

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.transformer.ChatServiceContext;
import org.springframework.ai.chat.prompt.transformer.PromptTransformer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * A PromptTransformingChatService implements the ChatService interface and performs
 * transformation of the prompt using a series of PromptTransformers. It also provides a
 * builder class for easier construction of the PromptTransformingChatService instance.
 *
 * @author Mark Pollack
 * @author Christian Tzolov
 * @since 1.0 M1
 */
public class PromptTransformingChatService implements ChatService {

	private ChatModel chatModel;

	private List<PromptTransformer> retrievers;

	private List<PromptTransformer> documentPostProcessors;

	private List<PromptTransformer> augmentors;

	private List<ChatServiceListener> chatServiceListeners;

	public PromptTransformingChatService(ChatModel chatModel, List<PromptTransformer> retrievers,
			List<PromptTransformer> documentPostProcessors, List<PromptTransformer> augmentors,
			List<ChatServiceListener> chatServiceListeners) {
		Objects.requireNonNull(chatModel, "chatModel must not be null");
		this.chatModel = chatModel;
		this.retrievers = retrievers;
		this.documentPostProcessors = documentPostProcessors;
		this.augmentors = augmentors;
		this.chatServiceListeners = chatServiceListeners;
	}

	public static Builder builder(ChatModel chatModel) {
		return new Builder().withChatModel(chatModel);
	}

	@Override
	public ChatServiceResponse call(ChatServiceContext chatServiceContext) {

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
		ChatResponse chatResponse = this.chatModel.call(chatServiceContext.getPrompt());

		// Invoke Listeners onComplete
		ChatServiceResponse chatServiceResponse = new ChatServiceResponse(chatServiceContext, chatResponse);
		for (ChatServiceListener listener : this.chatServiceListeners) {
			listener.onComplete(chatServiceResponse);
		}
		return chatServiceResponse;
	}

	public static class Builder {

		private ChatModel chatModel;

		private List<PromptTransformer> retrievers = new ArrayList<>();

		private List<PromptTransformer> documentPostProcessors = new ArrayList<>();

		private List<PromptTransformer> augmentors = new ArrayList<>();

		private List<ChatServiceListener> chatServiceListeners = new ArrayList<>();

		public Builder withChatModel(ChatModel chatModel) {
			this.chatModel = chatModel;
			return this;
		}

		public Builder withRetrievers(List<PromptTransformer> retrievers) {
			this.retrievers = retrievers;
			return this;
		}

		public Builder withContentPostProcessors(List<PromptTransformer> documentPostProcessors) {
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

		public PromptTransformingChatService build() {
			return new PromptTransformingChatService(chatModel, retrievers, documentPostProcessors, augmentors,
					chatServiceListeners);
		}

	}

}