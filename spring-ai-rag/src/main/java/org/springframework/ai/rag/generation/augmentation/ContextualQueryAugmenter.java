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

package org.springframework.ai.rag.generation.augmentation;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.rag.util.PromptAssert;
import org.springframework.util.Assert;

/**
 * Augments the user query with contextual data from the content of the provided
 * documents.
 *
 * <p>
 * Example usage: <pre>{@code
 * QueryAugmenter augmenter = ContextualQueryAugmenter.builder()
 *    .allowEmptyContext(false)
 *    .build();
 * Query augmentedQuery = augmenter.augment(query, documents);
 * }</pre>
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public final class ContextualQueryAugmenter implements QueryAugmenter {

	private static final Logger logger = LoggerFactory.getLogger(ContextualQueryAugmenter.class);

	private static final PromptTemplate DEFAULT_PROMPT_TEMPLATE = new PromptTemplate("""
			Context information is below.

			---------------------
			{context}
			---------------------

			Given the context information and no prior knowledge, answer the query.

			Follow these rules:

			1. If the answer is not in the context, just say that you don't know.
			2. Avoid statements like "Based on the context..." or "The provided information...".

			Query: {query}

			Answer:
			""");

	private static final PromptTemplate DEFAULT_EMPTY_CONTEXT_PROMPT_TEMPLATE = new PromptTemplate("""
			The user query is outside your knowledge base.
			Politely inform the user that you can't answer it.
			""");

	private static final boolean DEFAULT_ALLOW_EMPTY_CONTEXT = false;

	/**
	 * Default document formatter that just joins document text with newlines
	 */
	private static final Function<List<Document>, String> DEFAULT_DOCUMENT_FORMATTER = documents -> documents.stream()
		.map(Document::getText)
		.collect(Collectors.joining(System.lineSeparator()));

	private final PromptTemplate promptTemplate;

	private final PromptTemplate emptyContextPromptTemplate;

	private final boolean allowEmptyContext;

	private final Function<List<Document>, String> documentFormatter;

	public ContextualQueryAugmenter(@Nullable PromptTemplate promptTemplate,
			@Nullable PromptTemplate emptyContextPromptTemplate, @Nullable Boolean allowEmptyContext,
			@Nullable Function<List<Document>, String> documentFormatter) {
		this.promptTemplate = promptTemplate != null ? promptTemplate : DEFAULT_PROMPT_TEMPLATE;
		this.emptyContextPromptTemplate = emptyContextPromptTemplate != null ? emptyContextPromptTemplate
				: DEFAULT_EMPTY_CONTEXT_PROMPT_TEMPLATE;
		this.allowEmptyContext = allowEmptyContext != null ? allowEmptyContext : DEFAULT_ALLOW_EMPTY_CONTEXT;
		this.documentFormatter = documentFormatter != null ? documentFormatter : DEFAULT_DOCUMENT_FORMATTER;
		PromptAssert.templateHasRequiredPlaceholders(this.promptTemplate, "query", "context");
	}

	@Override
	public Query augment(Query query, List<Document> documents) {
		Assert.notNull(query, "query cannot be null");
		Assert.notNull(documents, "documents cannot be null");

		logger.debug("Augmenting query with contextual data");

		if (documents.isEmpty()) {
			return augmentQueryWhenEmptyContext(query);
		}

		// 1. Collect content from documents.
		String documentContext = this.documentFormatter.apply(documents);

		// 2. Define prompt parameters.
		Map<String, Object> promptParameters = Map.of("query", query.text(), "context", documentContext);

		// 3. Augment user prompt with document context.
		return new Query(this.promptTemplate.render(promptParameters));
	}

	private Query augmentQueryWhenEmptyContext(Query query) {
		if (this.allowEmptyContext) {
			logger.debug("Empty context is allowed. Returning the original query.");
			return query;
		}
		logger.debug("Empty context is not allowed. Returning a specific query for empty context.");
		return new Query(this.emptyContextPromptTemplate.render());
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private @Nullable PromptTemplate promptTemplate;

		private @Nullable PromptTemplate emptyContextPromptTemplate;

		private @Nullable Boolean allowEmptyContext;

		private @Nullable Function<List<Document>, String> documentFormatter;

		public Builder promptTemplate(PromptTemplate promptTemplate) {
			this.promptTemplate = promptTemplate;
			return this;
		}

		public Builder emptyContextPromptTemplate(PromptTemplate emptyContextPromptTemplate) {
			this.emptyContextPromptTemplate = emptyContextPromptTemplate;
			return this;
		}

		public Builder allowEmptyContext(Boolean allowEmptyContext) {
			this.allowEmptyContext = allowEmptyContext;
			return this;
		}

		public Builder documentFormatter(Function<List<Document>, String> documentFormatter) {
			this.documentFormatter = documentFormatter;
			return this;
		}

		public ContextualQueryAugmenter build() {
			return new ContextualQueryAugmenter(this.promptTemplate, this.emptyContextPromptTemplate,
					this.allowEmptyContext, this.documentFormatter);
		}

	}

}
