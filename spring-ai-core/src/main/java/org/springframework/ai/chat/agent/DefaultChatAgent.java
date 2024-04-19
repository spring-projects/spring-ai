package org.springframework.ai.chat.agent;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.prompt.transformer.PromptContext;
import org.springframework.ai.chat.prompt.transformer.PromptTransformer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DefaultChatAgent implements ChatAgent {

	private ChatClient chatClient;

	private List<PromptTransformer> retrievers;

	private List<PromptTransformer> documentPostProcessors;

	private List<PromptTransformer> augmentors;

	private List<ChatAgentListener> chatAgentListeners;

	public DefaultChatAgent(ChatClient chatClient, List<PromptTransformer> retrievers,
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
	public AgentResponse call(PromptContext promptContext) {

		// Perform retrieval of documents and messages
		for (PromptTransformer retriever : retrievers) {
			promptContext = retriever.transform(promptContext);
		}

		// Perform post procesing of all retrieved documents and messages
		for (PromptTransformer documentPostProcessor : documentPostProcessors) {
			promptContext = documentPostProcessor.transform(promptContext);
		}

		// Perform prompt augmentation
		for (PromptTransformer augmentor : augmentors) {
			promptContext = augmentor.transform(promptContext);
		}

		// Perform generation
		ChatResponse chatResponse = chatClient.call(promptContext.getPrompt());

		// Invoke Listeners onComplete
		AgentResponse agentResponse = new AgentResponse(promptContext, chatResponse);
		for (ChatAgentListener listener : chatAgentListeners) {
			listener.onComplete(agentResponse);
		}
		return agentResponse;
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

		public DefaultChatAgent build() {
			return new DefaultChatAgent(chatClient, retrievers, documentPostProcessors, augmentors, chatAgentListeners);
		}

	}

}