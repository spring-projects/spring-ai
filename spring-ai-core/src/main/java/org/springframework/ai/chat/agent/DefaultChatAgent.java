package org.springframework.ai.chat.agent;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.agent.transformer.PromptContextTransformer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class DefaultChatAgent implements ChatAgent {

	private ChatClient chatClient;

	private List<PromptContextTransformer> retrievers;

	private List<PromptContextTransformer> documentPostProcessors;

	private List<PromptContextTransformer> augmentors;

	private List<ChatAgentListener> chatAgentListeners;

	public DefaultChatAgent(ChatClient chatClient, List<PromptContextTransformer> retrievers,
			List<PromptContextTransformer> documentPostProcessors, List<PromptContextTransformer> augmentors,
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
		for (PromptContextTransformer retriever : retrievers) {
			promptContext = retriever.transform(promptContext);
		}

		// Perform post procesing of all retrieved documents and messages
		for (PromptContextTransformer documentPostProcessor : documentPostProcessors) {
			promptContext = documentPostProcessor.transform(promptContext);
		}

		// Perform prompt augmentation
		for (PromptContextTransformer augmentor : augmentors) {
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

		private List<PromptContextTransformer> retrievers = new ArrayList<>();

		private List<PromptContextTransformer> documentPostProcessors = new ArrayList<>();

		private List<PromptContextTransformer> augmentors = new ArrayList<>();

		private List<ChatAgentListener> chatAgentListeners = new ArrayList<>();

		public DefaultChatAgentBuilder withChatClient(ChatClient chatClient) {
			this.chatClient = chatClient;
			return this;
		}

		public DefaultChatAgentBuilder withRetrievers(List<PromptContextTransformer> retrievers) {
			this.retrievers = retrievers;
			return this;
		}

		public DefaultChatAgentBuilder withDocumentPostProcessors(
				List<PromptContextTransformer> documentPostProcessors) {
			this.documentPostProcessors = documentPostProcessors;
			return this;
		}

		public DefaultChatAgentBuilder withAugmentors(List<PromptContextTransformer> augmentors) {
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