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

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.util.PromptAssert;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * Uses a large language model to rewrite a user query to provide better results when
 * querying a target system, such as a vector store or a web search engine.
 * <p>
 * This transformer is useful when the user query is verbose, ambiguous, or contains
 * irrelevant information that may affect the quality of the search results.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 * @see <a href="https://arxiv.org/pdf/2305.14283">arXiv:2305.14283</a>
 */
public class RewriteQueryTransformer implements QueryTransformer {

	private static final Logger logger = LoggerFactory.getLogger(RewriteQueryTransformer.class);

	private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = new PromptTemplate("""
			Given a user query, rewrite it to provide better results when querying a {target}.
			Remove any irrelevant information, and ensure the query is concise and specific.

			Original query:
			{query}

			Rewritten query:
			""");

	private static final String DEFAULT_TARGET = "vector store";

	private final ChatClient chatClient;

	private final PromptTemplate promptTemplate;

	private final String targetSearchSystem;

	public RewriteQueryTransformer(ChatClient.Builder chatClientBuilder, @Nullable PromptTemplate promptTemplate,
			@Nullable String targetSearchSystem) {
		Assert.notNull(chatClientBuilder, "chatClientBuilder cannot be null");

		this.chatClient = chatClientBuilder.build();
		this.promptTemplate = promptTemplate != null ? promptTemplate : DEFAULT_PROMPT_TEMPLATE;
		this.targetSearchSystem = targetSearchSystem != null ? targetSearchSystem : DEFAULT_TARGET;

		PromptAssert.templateHasRequiredPlaceholders(this.promptTemplate, "target", "query");
	}

	@Override
	public Query transform(Query query) {
		Assert.notNull(query, "query cannot be null");

		logger.debug("Rewriting query to optimize for querying a {}.", this.targetSearchSystem);

		var rewrittenQueryText = this.chatClient.prompt()
			.user(user -> user.text(this.promptTemplate.getTemplate())
				.param("target", this.targetSearchSystem)
				.param("query", query.text()))
			.call()
			.content();

		if (!StringUtils.hasText(rewrittenQueryText)) {
			logger.warn("Query rewrite result is null/empty. Returning the input query unchanged.");
			return query;
		}

		return query.mutate().text(rewrittenQueryText).build();
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private ChatClient.@Nullable Builder chatClientBuilder;

		private @Nullable PromptTemplate promptTemplate;

		private @Nullable String targetSearchSystem;

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

		public Builder targetSearchSystem(String targetSearchSystem) {
			this.targetSearchSystem = targetSearchSystem;
			return this;
		}

		public RewriteQueryTransformer build() {
			Assert.state(this.chatClientBuilder != null, "chatClientBuilder cannot be null");
			return new RewriteQueryTransformer(this.chatClientBuilder, this.promptTemplate, this.targetSearchSystem);
		}

	}

}
