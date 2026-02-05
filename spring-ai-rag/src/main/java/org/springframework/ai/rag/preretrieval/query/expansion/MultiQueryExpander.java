/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.rag.preretrieval.query.expansion;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.util.PromptAssert;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;

/**
 * Uses a large language model to expand a query into multiple semantically diverse
 * variations to capture different perspectives, useful for retrieving additional
 * contextual information and increasing the chances of finding relevant results.
 *
 * <p>
 * Example usage: <pre>{@code
 * MultiQueryExpander expander = MultiQueryExpander.builder()
 *    .chatClientBuilder(chatClientBuilder)
 *    .numberOfQueries(3)
 *    .build();
 * List<Query> queries = expander.expand(new Query("How to run a Spring Boot app?"));
 * }</pre>
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public final class MultiQueryExpander implements QueryExpander {

	private static final Logger logger = LoggerFactory.getLogger(MultiQueryExpander.class);

	private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = new PromptTemplate("""
			You are an expert at information retrieval and search optimization.
			Your task is to generate {number} different versions of the given query.

			Each variant must cover different perspectives or aspects of the topic,
			while maintaining the core intent of the original query. The goal is to
			expand the search space and improve the chances of finding relevant information.

			Do not explain your choices or add any other text.
			Provide the query variants separated by newlines.

			Original query: {query}

			Query variants:
			""");

	private static final Boolean DEFAULT_INCLUDE_ORIGINAL = true;

	private static final Integer DEFAULT_NUMBER_OF_QUERIES = 3;

	private final ChatClient chatClient;

	private final PromptTemplate promptTemplate;

	private final boolean includeOriginal;

	private final int numberOfQueries;

	public MultiQueryExpander(ChatClient.Builder chatClientBuilder, @Nullable PromptTemplate promptTemplate,
			@Nullable Boolean includeOriginal, @Nullable Integer numberOfQueries) {
		Assert.notNull(chatClientBuilder, "chatClientBuilder cannot be null");

		this.chatClient = chatClientBuilder.build();
		this.promptTemplate = promptTemplate != null ? promptTemplate : DEFAULT_PROMPT_TEMPLATE;
		this.includeOriginal = includeOriginal != null ? includeOriginal : DEFAULT_INCLUDE_ORIGINAL;
		this.numberOfQueries = numberOfQueries != null ? numberOfQueries : DEFAULT_NUMBER_OF_QUERIES;

		PromptAssert.templateHasRequiredPlaceholders(this.promptTemplate, "number", "query");
	}

	@Override
	public List<Query> expand(Query query) {
		Assert.notNull(query, "query cannot be null");

		logger.debug("Generating {} query variants", this.numberOfQueries);

		var response = this.chatClient.prompt()
			.user(user -> user.text(this.promptTemplate.getTemplate())
				.param("number", this.numberOfQueries)
				.param("query", query.text()))
			.call()
			.content();

		if (response == null) {
			logger.warn("Query expansion result is null. Returning the input query unchanged.");
			return List.of(query);
		}

		var queryVariants = Arrays.stream(response.split("\n"))
				.filter(StringUtils::hasText)
				.toList();

		if (CollectionUtils.isEmpty(queryVariants) || this.numberOfQueries > queryVariants.size()) {
			logger.warn(
					"Query expansion result does not contain the requested {} variants. Returning the input query unchanged.",
					this.numberOfQueries);
			return List.of(query);
		}

		var queries = queryVariants.stream()
			.filter(StringUtils::hasText)
			.map(queryText -> query.mutate().text(queryText).build())
			.collect(Collectors.toList());

		if (this.includeOriginal) {
			logger.debug("Including the original query in the result");
			queries.add(0, query);
		}

		return queries;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private ChatClient.@Nullable Builder chatClientBuilder;

		private @Nullable PromptTemplate promptTemplate;

		private @Nullable Boolean includeOriginal;

		private @Nullable Integer numberOfQueries;

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

		public Builder includeOriginal(Boolean includeOriginal) {
			this.includeOriginal = includeOriginal;
			return this;
		}

		public Builder numberOfQueries(Integer numberOfQueries) {
			this.numberOfQueries = numberOfQueries;
			return this;
		}

		public MultiQueryExpander build() {
			Assert.state(this.chatClientBuilder != null, "chatClientBuilder cannot be null");
			return new MultiQueryExpander(this.chatClientBuilder, this.promptTemplate, this.includeOriginal,
					this.numberOfQueries);
		}

	}

}
