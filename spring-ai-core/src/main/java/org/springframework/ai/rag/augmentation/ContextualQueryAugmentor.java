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

package org.springframework.ai.rag.augmentation;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.model.Content;
import org.springframework.ai.rag.Query;
import org.springframework.ai.util.PromptAssert;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Augments the user query with contextual data.
 *
 * <p>
 * Example usage: <pre>{@code
 * QueryAugmentor augmentor = ContextualQueryAugmentor.builder()
 *    .promptTemplate(promptTemplate)
 *    .emptyContextPromptTemplate(emptyContextPromptTemplate)
 *    .allowEmptyContext(allowEmptyContext)
 *    .build();
 * Query augmentedQuery = augmentor.augment(query, documents);
 * }</pre>
 *
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class ContextualQueryAugmentor implements QueryAugmentor {

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

	private static final boolean DEFAULT_ALLOW_EMPTY_CONTEXT = true;

	private final PromptTemplate promptTemplate;

	private final PromptTemplate emptyContextPromptTemplate;

	private final boolean allowEmptyContext;

	public ContextualQueryAugmentor(@Nullable PromptTemplate promptTemplate,
			@Nullable PromptTemplate emptyContextPromptTemplate, @Nullable Boolean allowEmptyContext) {
		this.promptTemplate = promptTemplate != null ? promptTemplate : DEFAULT_PROMPT_TEMPLATE;
		this.emptyContextPromptTemplate = emptyContextPromptTemplate != null ? emptyContextPromptTemplate
				: DEFAULT_EMPTY_CONTEXT_PROMPT_TEMPLATE;
		this.allowEmptyContext = allowEmptyContext != null ? allowEmptyContext : DEFAULT_ALLOW_EMPTY_CONTEXT;
		PromptAssert.templateHasRequiredPlaceholders(this.promptTemplate, "query", "context");
	}

	@Override
	public Query augment(Query query, List<Document> documents) {
		Assert.notNull(query, "query cannot be null");
		Assert.notNull(documents, "documents cannot be null");

		if (documents.isEmpty()) {
			return augmentQueryWhenEmptyContext(query);
		}

		// 1. Join documents.
		String documentContext = documents.stream()
			.map(Content::getContent)
			.collect(Collectors.joining(System.lineSeparator()));

		// 2. Define prompt parameters.
		Map<String, Object> promptParameters = Map.of("query", query.text(), "context", documentContext);

		// 3. Augment user prompt with document context.
		return new Query(this.promptTemplate.render(promptParameters));
	}

	private Query augmentQueryWhenEmptyContext(Query query) {
		if (this.allowEmptyContext) {
			return query;
		}
		return new Query(this.emptyContextPromptTemplate.render());
	}

	public static Builder builder() {
		return new Builder();
	}

	public static class Builder {

		private PromptTemplate promptTemplate;

		private PromptTemplate emptyContextPromptTemplate;

		private Boolean allowEmptyContext;

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

		public ContextualQueryAugmentor build() {
			return new ContextualQueryAugmentor(this.promptTemplate, this.emptyContextPromptTemplate,
					this.allowEmptyContext);
		}

	}

}
