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

package org.springframework.ai.chat.transformer;

import java.util.List;
import java.util.Map;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.util.Assert;

/**
 * Keyword extractor that uses generative to extract 'excerpt_keywords' metadata field.
 *
 * @author Christian Tzolov
 */
public class KeywordMetadataEnricher implements DocumentTransformer {

	public static final String CONTEXT_STR_PLACEHOLDER = "context_str";

	public static final String KEYWORDS_TEMPLATE = """
			{context_str}. Give %s unique keywords for this
			document. Format as comma separated. Keywords: """;

	private static final String EXCERPT_KEYWORDS_METADATA_KEY = "excerpt_keywords";

	/**
	 * Model predictor
	 */
	private final ChatModel chatModel;

	/**
	 * The number of keywords to extract.
	 */
	private final int keywordCount;

	public KeywordMetadataEnricher(ChatModel chatModel, int keywordCount) {
		Assert.notNull(chatModel, "ChatModel must not be null");
		Assert.isTrue(keywordCount >= 1, "Document count must be >= 1");

		this.chatModel = chatModel;
		this.keywordCount = keywordCount;
	}

	@Override
	public List<Document> apply(List<Document> documents) {
		for (Document document : documents) {

			var template = new PromptTemplate(String.format(KEYWORDS_TEMPLATE, this.keywordCount));
			Prompt prompt = template.create(Map.of(CONTEXT_STR_PLACEHOLDER, document.getText()));
			String keywords = this.chatModel.call(prompt).getResult().getOutput().getText();
			document.getMetadata().putAll(Map.of(EXCERPT_KEYWORDS_METADATA_KEY, keywords));
		}
		return documents;
	}

}
