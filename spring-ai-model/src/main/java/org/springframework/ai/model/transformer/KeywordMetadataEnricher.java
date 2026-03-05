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

package org.springframework.ai.model.transformer;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.util.Assert;

/**
 * Keyword extractor that uses generative to extract 'excerpt_keywords' metadata field.
 *
 * @author Christian Tzolov
 * @author YunKui Lu
 */
public class KeywordMetadataEnricher implements DocumentTransformer {

	private static final Logger logger = LoggerFactory.getLogger(KeywordMetadataEnricher.class);

	public static final String CONTEXT_STR_PLACEHOLDER = "context_str";

	public static final String KEYWORDS_TEMPLATE = """
			{context_str}. Give %s unique keywords for this
			document. Format as comma separated. Keywords: """;

	public static final String EXCERPT_KEYWORDS_METADATA_KEY = "excerpt_keywords";

	/**
	 * Model predictor
	 */
	private final ChatModel chatModel;

	/**
	 * The prompt template to use for keyword extraction.
	 */
	private final PromptTemplate keywordsTemplate;

	/**
	 * Create a new {@link KeywordMetadataEnricher} instance.
	 * @param chatModel the model predictor to use for keyword extraction.
	 * @param keywordCount the number of keywords to extract.
	 */
	public KeywordMetadataEnricher(ChatModel chatModel, int keywordCount) {
		Assert.notNull(chatModel, "chatModel must not be null");
		Assert.isTrue(keywordCount >= 1, "keywordCount must be >= 1");

		this.chatModel = chatModel;
		this.keywordsTemplate = new PromptTemplate(String.format(KEYWORDS_TEMPLATE, keywordCount));
	}

	/**
	 * Create a new {@link KeywordMetadataEnricher} instance.
	 * @param chatModel the model predictor to use for keyword extraction.
	 * @param keywordsTemplate the prompt template to use for keyword extraction.
	 */
	public KeywordMetadataEnricher(ChatModel chatModel, PromptTemplate keywordsTemplate) {
		Assert.notNull(chatModel, "chatModel must not be null");
		Assert.notNull(keywordsTemplate, "keywordsTemplate must not be null");

		this.chatModel = chatModel;
		this.keywordsTemplate = keywordsTemplate;
	}

	@Override
	public List<Document> apply(List<Document> documents) {
		for (Document document : documents) {
			String text = document.getText();
			Map<String, Object> vars = new HashMap<>();
			if (text != null) {
				vars.put(CONTEXT_STR_PLACEHOLDER, text);
			}
			Prompt prompt = this.keywordsTemplate.create(vars);
			Generation generation = this.chatModel.call(prompt).getResult();
			if (generation != null) {
				String keywords = generation.getOutput().getText();
				if (keywords != null) {
					document.getMetadata().put(EXCERPT_KEYWORDS_METADATA_KEY, keywords);
				}
			}
		}
		return documents;
	}

	// Exposed for testing purposes
	PromptTemplate getKeywordsTemplate() {
		return this.keywordsTemplate;
	}

	public static Builder builder(ChatModel chatModel) {
		return new Builder(chatModel);
	}

	public static final class Builder {

		private final ChatModel chatModel;

		private int keywordCount;

		private @Nullable PromptTemplate keywordsTemplate;

		public Builder(ChatModel chatModel) {
			Assert.notNull(chatModel, "The chatModel must not be null");
			this.chatModel = chatModel;
		}

		public Builder keywordCount(int keywordCount) {
			Assert.isTrue(keywordCount >= 1, "The keywordCount must be >= 1");
			this.keywordCount = keywordCount;
			return this;
		}

		public Builder keywordsTemplate(PromptTemplate keywordsTemplate) {
			Assert.notNull(keywordsTemplate, "The keywordsTemplate must not be null");
			this.keywordsTemplate = keywordsTemplate;
			return this;
		}

		public KeywordMetadataEnricher build() {
			if (this.keywordsTemplate != null) {

				if (this.keywordCount != 0) {
					logger.warn("keywordCount will be ignored as keywordsTemplate is set.");
				}

				return new KeywordMetadataEnricher(this.chatModel, this.keywordsTemplate);
			}

			return new KeywordMetadataEnricher(this.chatModel, this.keywordCount);
		}

	}

}
