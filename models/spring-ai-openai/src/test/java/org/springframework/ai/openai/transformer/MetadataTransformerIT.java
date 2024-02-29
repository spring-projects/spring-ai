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

package org.springframework.ai.openai.transformer;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.OpenAiChatClient;
import org.springframework.ai.transformer.ContentFormatTransformer;
import org.springframework.ai.transformer.KeywordMetadataEnricher;
import org.springframework.ai.transformer.SummaryMetadataEnricher;
import org.springframework.ai.transformer.SummaryMetadataEnricher.SummaryType;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
@SpringBootTest
public class MetadataTransformerIT {

	@Autowired
	KeywordMetadataEnricher keywordMetadataEnricher;

	@Autowired
	SummaryMetadataEnricher summaryMetadataEnricher;

	@Autowired
	ContentFormatTransformer contentFormatTransformer;

	@Autowired
	DefaultContentFormatter defaultContentFormatter;

	Document document1 = new Document("Somewhere in the Andes, they believe to this very day that the"
			+ " future is behind you. It comes up from behind your back, surprising and unforeseeable, while the past "
			+ " is always before your eyes, that which has already happened. When they talk about the past, the people of"
			+ " the Aymara tribe point in front of them. You walk forward facing the past and you turn back toward the future.",
			new HashMap<>(Map.of("key", "value")));

	Document document2 = new Document(
			"The Spring Framework is divided into modules. Applications can choose which modules"
					+ " they need. At the heart are the modules of the core container, including a configuration generative and a "
					+ "dependency injection mechanism. Beyond that, the Spring Framework provides foundational support "
					+ " for different application architectures, including messaging, transactional data and persistence, "
					+ "and web. It also includes the Servlet-based Spring MVC web framework and, in parallel, the Spring "
					+ "WebFlux reactive web framework.");

	@Test
	public void testKeywordExtractor() {

		var updatedDocuments = keywordMetadataEnricher.apply(List.of(document1, document2));

		List<Map<String, Object>> keywords = updatedDocuments.stream().map(d -> d.getMetadata()).toList();

		assertThat(updatedDocuments.size()).isEqualTo(2);
		var keywords1 = keywords.get(0);
		var keywords2 = keywords.get(1);
		assertThat(keywords1).containsKeys("excerpt_keywords");
		assertThat(keywords2).containsKeys("excerpt_keywords");

		assertThat((String) keywords1.get("excerpt_keywords")).contains("Andes", "Aymara");
		assertThat((String) keywords2.get("excerpt_keywords")).contains("Spring Framework", "dependency injection");
	}

	@Test
	public void testSummaryExtractor() {

		var updatedDocuments = summaryMetadataEnricher.apply(List.of(document1, document2));

		List<Map<String, Object>> summaries = updatedDocuments.stream().map(d -> d.getMetadata()).toList();

		assertThat(summaries.size()).isEqualTo(2);
		var summary1 = summaries.get(0);
		var summary2 = summaries.get(1);
		assertThat(summary1).containsKeys("section_summary", "next_section_summary");
		assertThat(summary1).doesNotContainKeys("prev_section_summary");
		assertThat(summary2).containsKeys("section_summary", "prev_section_summary");
		assertThat(summary2).doesNotContainKeys("next_section_summary");

		assertThat((String) summary1.get("section_summary")).isNotEmpty();
		assertThat((String) summary1.get("next_section_summary")).isNotEmpty();
		assertThat((String) summary2.get("section_summary")).isNotEmpty();
		assertThat((String) summary2.get("prev_section_summary")).isNotEmpty();

		assertThat((String) summary1.get("section_summary")).isEqualTo((String) summary2.get("prev_section_summary"));
		assertThat((String) summary1.get("next_section_summary")).isEqualTo((String) summary2.get("section_summary"));
	}

	@Test
	public void testContentFormatEnricher() {

		assertThat(((DefaultContentFormatter) document1.getContentFormatter()).getExcludedEmbedMetadataKeys())
			.doesNotContain("NewEmbedKey");
		assertThat(((DefaultContentFormatter) document1.getContentFormatter()).getExcludedInferenceMetadataKeys())
			.doesNotContain("NewInferenceKey");

		assertThat(((DefaultContentFormatter) document2.getContentFormatter()).getExcludedEmbedMetadataKeys())
			.doesNotContain("NewEmbedKey");
		assertThat(((DefaultContentFormatter) document2.getContentFormatter()).getExcludedInferenceMetadataKeys())
			.doesNotContain("NewInferenceKey");

		List<Document> enrichedDocuments = contentFormatTransformer.apply(List.of(document1, document2));

		assertThat(enrichedDocuments.size()).isEqualTo(2);
		var doc1 = enrichedDocuments.get(0);
		var doc2 = enrichedDocuments.get(1);

		assertThat(doc1).isEqualTo(document1);
		assertThat(doc2).isEqualTo(document2);

		assertThat(((DefaultContentFormatter) doc1.getContentFormatter()).getTextTemplate())
			.isSameAs(defaultContentFormatter.getTextTemplate());
		assertThat(((DefaultContentFormatter) doc1.getContentFormatter()).getExcludedEmbedMetadataKeys())
			.contains("NewEmbedKey");
		assertThat(((DefaultContentFormatter) doc1.getContentFormatter()).getExcludedInferenceMetadataKeys())
			.contains("NewInferenceKey");

		assertThat(((DefaultContentFormatter) doc2.getContentFormatter()).getTextTemplate())
			.isSameAs(defaultContentFormatter.getTextTemplate());
		assertThat(((DefaultContentFormatter) doc2.getContentFormatter()).getExcludedEmbedMetadataKeys())
			.contains("NewEmbedKey");
		assertThat(((DefaultContentFormatter) doc2.getContentFormatter()).getExcludedInferenceMetadataKeys())
			.contains("NewInferenceKey");

	}

	@SpringBootConfiguration
	public static class OpenAiTestConfiguration {

		@Bean
		public OpenAiApi openAiApi() throws IOException {
			String apiKey = System.getenv("OPENAI_API_KEY");
			if (!StringUtils.hasText(apiKey)) {
				throw new IllegalArgumentException(
						"You must provide an API key.  Put it in an environment variable under the name OPENAI_API_KEY");
			}
			return new OpenAiApi(apiKey);
		}

		@Bean
		public OpenAiChatClient openAiChatClient(OpenAiApi openAiApi) {
			OpenAiChatClient openAiChatClient = new OpenAiChatClient(openAiApi);
			return openAiChatClient;
		}

		@Bean
		public KeywordMetadataEnricher keywordMetadata(OpenAiChatClient aiClient) {
			return new KeywordMetadataEnricher(aiClient, 5);
		}

		@Bean
		public SummaryMetadataEnricher summaryMetadata(OpenAiChatClient aiClient) {
			return new SummaryMetadataEnricher(aiClient,
					List.of(SummaryType.PREVIOUS, SummaryType.CURRENT, SummaryType.NEXT));
		}

		@Bean
		public DefaultContentFormatter defaultContentFormatter() {
			return DefaultContentFormatter.builder()
				.withExcludedEmbedMetadataKeys("NewEmbedKey")
				.withExcludedInferenceMetadataKeys("NewInferenceKey")
				.build();
		}

		@Bean
		public ContentFormatTransformer contentFormatTransformer(DefaultContentFormatter defaultContentFormatter) {
			return new ContentFormatTransformer(defaultContentFormatter, false);
		}

	}

}
