/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.rag.preretrieval.query.transformation;

import java.util.List;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.util.PromptAssert;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Uses a large language model to compress a conversation history and a follow-up query
 * into a standalone query that captures the essence of the conversation.
 * <p>
 * This transformer is useful when the conversation history is long and the follow-up
 * query is related to the conversation context.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class CompressionQueryTransformer implements QueryTransformer {

	private static final Logger logger = LoggerFactory.getLogger(CompressionQueryTransformer.class);

	private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = new PromptTemplate("""
			Given the following conversation history and a follow-up query, your task is to synthesize
			a concise, standalone query that incorporates the context from the history.
			Ensure the standalone query is clear, specific, and maintains the user's intent.

			Conversation history:
			{history}

			Follow-up query:
			{query}

			Standalone query:
			""");

	private final ChatClient chatClient;

	private final PromptTemplate promptTemplate;

	public CompressionQueryTransformer(ChatClient.Builder chatClientBuilder, @Nullable PromptTemplate promptTemplate) {
		Assert.notNull(chatClientBuilder, "chatClientBuilder cannot be null");

		this.chatClient = chatClientBuilder.build();
		this.promptTemplate = promptTemplate != null ? promptTemplate : DEFAULT_PROMPT_TEMPLATE;

		PromptAssert.templateHasRequiredPlaceholders(this.promptTemplate, "history", "query");
	}

	@Override
	public Query transform(Query query) {
		Assert.notNull(query, "query cannot be null");

		logger.debug("Compressing conversation history and follow-up query into a standalone query");

		var compressedQueryText = this.chatClient.prompt()
			.user(user -> user.text(this.promptTemplate.getTemplate())
				.param("history", formatConversationHistory(query.history()))
				.param("query", query.text()))
			.call()
			.content();

		if (!StringUtils.hasText(compressedQueryText)) {
			logger.warn("Query compression result is null/empty. Returning the input query unchanged.");
			return query;
		}

		return query.mutate().text(compressedQueryText).build();
	}

	private String formatConversationHistory(List<Message> history) {
		if (history.isEmpty()) {
			return "";
		}

		return history.stream()
			.filter(message -> message.getMessageType().equals(MessageType.USER)
					|| message.getMessageType().equals(MessageType.ASSISTANT))
			.map(message -> "%s: %s".formatted(message.getMessageType(), message.getText()))
			.collect(Collectors.joining("\n"));
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private ChatClient.@Nullable Builder chatClientBuilder;

		private @Nullable PromptTemplate promptTemplate;

		private Builder() {
		}

		public Builder chatClientBuilder(ChatClient.Builder chatClientBuilder) {
			this.chatClientBuilder = chatClientBuilder;
			return this;
		}

		public Builder promptTemplate(PromptTemplate promptTemplate) {
			this.promptTemplate = promptTemplate;
			return this;
		}

		public CompressionQueryTransformer build() {
			Assert.state(this.chatClientBuilder != null, "chatClientBuilder cannot be null");
			return new CompressionQueryTransformer(this.chatClientBuilder, this.promptTemplate);
		}

	}

}
