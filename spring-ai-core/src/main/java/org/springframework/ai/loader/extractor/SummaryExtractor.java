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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.ai.client.AiClient;
import org.springframework.ai.document.ContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.prompt.Prompt;
import org.springframework.ai.prompt.PromptTemplate;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;

/**
 * Title extractor with adjacent sharing that uses LLM to extract 'section_summary',
 * 'prev_section_summary', 'next_section_summary' metadata fields.
 *
 * @author Christian Tzolov
 */
public class SummaryExtractor implements MetadataFeatureExtractor {

	public static final String DEFAULT_SUMMARY_EXTRACT_TEMPLATE = """
			Here is the content of the section:
			{context_str}

			Summarize the key topics and entities of the section.

			Summary: """;

	public enum SummaryType {

		PREVIOUS, CURRENT, NEXT;

	}

	/**
	 * LLM predictor
	 */
	private final AiClient aiClient;

	/**
	 * Number of documents from front to use for title extraction
	 */
	private final List<SummaryType> summaryTypes;

	private final ContentFormatter.MetadataMode metadataMode;

	/**
	 * Template for summary extraction.
	 */
	private final String summaryTemplate;

	public SummaryExtractor(AiClient aiClient, List<SummaryType> summaryTypes) {
		this(aiClient, summaryTypes, DEFAULT_SUMMARY_EXTRACT_TEMPLATE, ContentFormatter.MetadataMode.ALL);
	}

	public SummaryExtractor(AiClient aiClient, List<SummaryType> summaryTypes, String summaryTemplate,
			ContentFormatter.MetadataMode metadataMode) {
		Assert.notNull(aiClient, "AiClient must not be null");
		Assert.hasText(summaryTemplate, "Summary template must not be empty");

		this.aiClient = aiClient;
		this.summaryTypes = CollectionUtils.isEmpty(summaryTypes) ? List.of(SummaryType.CURRENT) : summaryTypes;
		this.metadataMode = metadataMode;
		this.summaryTemplate = summaryTemplate;
	}

	@Override
	public List<Map<String, Object>> extract(List<Document> documents) {

		List<String> documentSummaries = new ArrayList<>();
		for (Document document : documents) {

			var documentContext = document.getFormattedContent(this.metadataMode);

			Prompt prompt = new PromptTemplate(this.summaryTemplate).create(Map.of("context_str", documentContext));
			documentSummaries.add(this.aiClient.generate(prompt).getGeneration().getText());
		}

		List<Map<String, Object>> result = new ArrayList<>();

		for (int i = 0; i < documentSummaries.size(); i++) {
			Map<String, Object> summaryMetadata = new HashMap<>();
			if (i > 0 && this.summaryTypes.contains(SummaryType.PREVIOUS)) {
				summaryMetadata.put("prev_section_summary", documentSummaries.get(i - 1));
			}
			if (i < (documentSummaries.size() - 1) && this.summaryTypes.contains(SummaryType.NEXT)) {
				summaryMetadata.put("next_section_summary", documentSummaries.get(i + 1));
			}
			if (this.summaryTypes.contains(SummaryType.CURRENT)) {
				summaryMetadata.put("section_summary", documentSummaries.get(i));
			}
			result.add(summaryMetadata);
		}

		return result;
	}

}
