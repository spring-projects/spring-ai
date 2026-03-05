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

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.chat.prompt.PromptTemplate;
import org.springframework.ai.document.Document;
import org.springframework.ai.document.DocumentTransformer;
import org.springframework.ai.document.MetadataMode;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Title extractor with adjacent sharing that uses generative to extract
 * 'section_summary', 'prev_section_summary', 'next_section_summary' metadata fields.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class SummaryMetadataEnricher implements DocumentTransformer {

	public static final String DEFAULT_SUMMARY_EXTRACT_TEMPLATE = """
			Here is the content of the section:
			{context_str}

			Summarize the key topics and entities of the section.

			Summary:""";

	private static final String SECTION_SUMMARY_METADATA_KEY = "section_summary";

	private static final String NEXT_SECTION_SUMMARY_METADATA_KEY = "next_section_summary";

	private static final String PREV_SECTION_SUMMARY_METADATA_KEY = "prev_section_summary";

	private static final String CONTEXT_STR_PLACEHOLDER = "context_str";

	/**
	 * AI client.
	 */
	private final ChatModel chatModel;

	/**
	 * Number of documents from front to use for title extraction.
	 */
	private final List<SummaryType> summaryTypes;

	private final MetadataMode metadataMode;

	/**
	 * Template for summary extraction.
	 */
	private final String summaryTemplate;

	public SummaryMetadataEnricher(ChatModel chatModel, List<SummaryType> summaryTypes) {
		this(chatModel, summaryTypes, DEFAULT_SUMMARY_EXTRACT_TEMPLATE, MetadataMode.ALL);
	}

	public SummaryMetadataEnricher(ChatModel chatModel, List<SummaryType> summaryTypes, String summaryTemplate,
			MetadataMode metadataMode) {
		Assert.notNull(chatModel, "ChatModel must not be null");
		Assert.hasText(summaryTemplate, "Summary template must not be empty");

		this.chatModel = chatModel;
		this.summaryTypes = CollectionUtils.isEmpty(summaryTypes) ? List.of(SummaryType.CURRENT) : summaryTypes;
		this.metadataMode = metadataMode;
		this.summaryTemplate = summaryTemplate;
	}

	@Override
	public List<Document> apply(List<Document> documents) {

		List<String> documentSummaries = new ArrayList<>();
		for (Document document : documents) {

			var documentContext = document.getFormattedContent(this.metadataMode);

			Prompt prompt = new PromptTemplate(this.summaryTemplate)
				.create(Map.of(CONTEXT_STR_PLACEHOLDER, documentContext));
			Generation generation = this.chatModel.call(prompt).getResult();
			documentSummaries
				.add(generation != null ? Objects.requireNonNullElse(generation.getOutput().getText(), "") : "");
		}

		for (int i = 0; i < documentSummaries.size(); i++) {
			Map<String, Object> summaryMetadata = getSummaryMetadata(i, documentSummaries);
			documents.get(i).getMetadata().putAll(summaryMetadata);
		}

		return documents;
	}

	private Map<String, Object> getSummaryMetadata(int i, List<String> documentSummaries) {
		Map<String, Object> summaryMetadata = new HashMap<>();
		if (i > 0 && this.summaryTypes.contains(SummaryType.PREVIOUS)) {
			summaryMetadata.put(PREV_SECTION_SUMMARY_METADATA_KEY, documentSummaries.get(i - 1));
		}
		if (i < (documentSummaries.size() - 1) && this.summaryTypes.contains(SummaryType.NEXT)) {
			summaryMetadata.put(NEXT_SECTION_SUMMARY_METADATA_KEY, documentSummaries.get(i + 1));
		}
		if (this.summaryTypes.contains(SummaryType.CURRENT)) {
			summaryMetadata.put(SECTION_SUMMARY_METADATA_KEY, documentSummaries.get(i));
		}
		return summaryMetadata;
	}

	public enum SummaryType {

		PREVIOUS, CURRENT, NEXT

	}

}
