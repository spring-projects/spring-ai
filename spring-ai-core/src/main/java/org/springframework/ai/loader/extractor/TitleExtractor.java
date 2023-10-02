/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.loader.extractor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.ai.client.AiClient;
import org.springframework.ai.document.Document;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.PromptTemplate;
import org.springframework.util.Assert;

/**
 * Title extractor that uses LLM to extract 'document_title' metadata field.
 *
 * @author Christian Tzolov
 */
public class TitleExtractor implements MetadataFeatureExtractor {

	public static final String DEFAULT_TITLE_DOCUMENT_TEMPLATE = """
			Context: {context_str}. Give a title that summarizes all of
			the unique entities, titles or themes found in the context. Title: """;

	public static final String DEFAULT_TITLE_COMBINE_TEMPLATE = """
			{context_str}. Based on the above candidate titles and content,
			what is the comprehensive title for this document? Title: """;

	private final AiClient aiClient;

	/**
	 * Number of documents from front to use for title extraction
	 */
	private final int documentCount;

	/**
	 * Template for document-level title clues extraction
	 */
	private final String titleTemplate;

	/**
	 * Template for combining node-level clues into a document-level title ???
	 */
	private final String combineTemplate;

	public TitleExtractor(AiClient aiClient, int documentCount) {
		this(aiClient, documentCount, DEFAULT_TITLE_DOCUMENT_TEMPLATE, DEFAULT_TITLE_COMBINE_TEMPLATE);
	}

	public TitleExtractor(AiClient aiClient, int documentCount, String titleTemplate, String combineTemplate) {
		Assert.notNull(aiClient, "AiClient must not be null");
		Assert.isTrue(documentCount >= 1, "Document count must be >= 1");
		Assert.hasText(titleTemplate, "Title template must not be empty");
		Assert.hasText(combineTemplate, "Combine template must not be empty");

		this.aiClient = aiClient;
		this.documentCount = documentCount;
		this.titleTemplate = titleTemplate;
		this.combineTemplate = combineTemplate;
	}

	@Override
	public List<Map<String, Object>> extract(List<Document> documents) {

		var documentsToExtractTitle = documents.subList(0, Math.min(documentCount, documents.size()));

		List<String> titleCandidates = new ArrayList<>();

		for (Document document : documentsToExtractTitle) {
			Prompt prompt = new PromptTemplate(this.titleTemplate).create(Map.of("context_str", document.getContent()));
			titleCandidates.add(this.aiClient.generate(prompt).getGeneration().getText());
		}

		var title = titleCandidates.get(0);
		if (titleCandidates.size() > 1) {
			var titles = titleCandidates.stream().collect(Collectors.joining(","));
			var combinePrompt = new PromptTemplate(this.combineTemplate).create(Map.of("context_str", titles));
			title = this.aiClient.generate(combinePrompt).getGeneration().getText();
		}

		List<Map<String, Object>> result = new ArrayList<>();

		Map<String, Object> titleMetadata = Map.of("document_title", title);
		documents.stream().forEach(doc -> result.add(titleMetadata));

		return result;
	}

}
