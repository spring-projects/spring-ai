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

package org.springframework.ai.openai.extractor;

import java.io.IOException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.theokanning.openai.service.OpenAiService;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.DefaultContentFormatter;
import org.springframework.ai.document.Document;
import org.springframework.ai.loader.extractor.KeywordExtractor;
import org.springframework.ai.loader.extractor.MetadataExtractor;
import org.springframework.ai.loader.extractor.MetadataFeatureExtractor;
import org.springframework.ai.loader.extractor.SummaryExtractor;
import org.springframework.ai.loader.extractor.TitleExtractor;
import org.springframework.ai.loader.extractor.SummaryExtractor.SummaryType;
import org.springframework.ai.openai.client.OpenAiClient;
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
public class MetadataExtractorIT {

	@Autowired
	KeywordExtractor keywordExtractor;

	@Autowired
	SummaryExtractor summaryExtractor;

	@Autowired
	TitleExtractor titleExtractor;

	@Autowired
	MetadataExtractor metadataExtractor;

	Document document1 = new Document("Somewhere in the Andes, they believe to this very day that the"
			+ " future is behind you. It comes up from behind your back, surprising and unforeseeable, while the past "
			+ " is always before your eyes, that which has already happened. When they talk about the past, the people of"
			+ " the Aymara tribe point in front of them. You walk forward facing the past and you turn back toward the future.",
			new HashMap<>(Map.of("key", "value")));

	Document document2 = new Document(
			"The Spring Framework is divided into modules. Applications can choose which modules"
					+ " they need. At the heart are the modules of the core container, including a configuration model and a "
					+ "dependency injection mechanism. Beyond that, the Spring Framework provides foundational support "
					+ " for different application architectures, including messaging, transactional data and persistence, "
					+ "and web. It also includes the Servlet-based Spring MVC web framework and, in parallel, the Spring "
					+ "WebFlux reactive web framework.");

	@Test
	public void testKeywordExtractor() {

		List<Map<String, Object>> keywords = keywordExtractor.extract(List.of(document1, document2));

		assertThat(keywords.size()).isEqualTo(2);
		var keywords1 = keywords.get(0);
		var keywords2 = keywords.get(1);
		assertThat(keywords1).containsKeys("excerpt_keywords");
		assertThat(keywords2).containsKeys("excerpt_keywords");

		assertThat((String) keywords1.get("excerpt_keywords")).contains("Andes", "Aymara");
		assertThat((String) keywords2.get("excerpt_keywords")).contains("Spring Framework", "dependency injection");
	}

	@Test
	public void testSummaryExtractor() {

		List<Map<String, Object>> summaries = summaryExtractor.extract(List.of(document1, document2));

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
	public void testTitleExtractor() {

		List<Map<String, Object>> titles = titleExtractor.extract(List.of(document1, document2));

		assertThat(titles.size()).isEqualTo(2);
		var title1 = titles.get(0);
		var title2 = titles.get(1);

		assertThat(title1).containsKeys("document_title");
		assertThat(title2).containsKeys("document_title");

		assertThat((String) title1.get("document_title")).isNotEmpty();
		assertThat((String) title2.get("document_title")).isNotEmpty();

		// assertThat((String) title1.get("document_title")).contains("Andes", "Aymara");
		// assertThat((String) title2.get("document_title")).contains("Spring Framework");
	}

	@Test
	public void testMetadataExtractor() {
		List<Document> enrichedDocuments = metadataExtractor.processDocuments(List.of(document1, document2), null);

		assertThat(enrichedDocuments.size()).isEqualTo(2);
		var doc1 = enrichedDocuments.get(0);
		var doc2 = enrichedDocuments.get(1);

		assertThat(doc1.getMetadata()).hasSize(5);
		assertThat(doc1.getMetadata()).containsKeys("document_title", "section_summary", "excerpt_keywords",
				"next_section_summary", "key");

		assertThat(doc2.getMetadata()).hasSize(4);
		assertThat(doc2.getMetadata()).containsKeys("document_title", "section_summary", "excerpt_keywords",
				"prev_section_summary");

		enrichedDocuments = metadataExtractor.processDocuments(List.of(document1, document2),
				DefaultContentFormatter.defaultConfig());

		assertThat(enrichedDocuments.size()).isEqualTo(2);
		doc1 = enrichedDocuments.get(0);
		doc2 = enrichedDocuments.get(1);

		assertThat(doc1.getMetadata()).hasSize(5);
		assertThat(doc1.getMetadata()).containsKeys("document_title", "section_summary", "excerpt_keywords",
				"next_section_summary", "key");

		assertThat(doc2.getMetadata()).hasSize(4);
		assertThat(doc2.getMetadata()).containsKeys("document_title", "section_summary", "excerpt_keywords",
				"prev_section_summary");
	}

	@SpringBootConfiguration
	public static class OpenAiTestConfiguration {

		@Bean
		public OpenAiService theoOpenAiService() throws IOException {
			String apiKey = System.getenv("OPENAI_API_KEY");
			if (!StringUtils.hasText(apiKey)) {
				throw new IllegalArgumentException(
						"You must provide an API key.  Put it in an environment variable under the name OPENAI_API_KEY");
			}
			OpenAiService openAiService = new OpenAiService(apiKey, Duration.ofSeconds(60));
			return openAiService;
		}

		@Bean
		public OpenAiClient openAiClient(OpenAiService theoOpenAiService) {
			OpenAiClient openAiClient = new OpenAiClient(theoOpenAiService);
			openAiClient.setTemperature(0.3);
			return openAiClient;
		}

		@Bean
		public KeywordExtractor keywordExtractor(OpenAiClient aiClient) {
			return new KeywordExtractor(aiClient, 5);
		}

		@Bean
		public SummaryExtractor summaryExtractor(OpenAiClient aiClient) {
			return new SummaryExtractor(aiClient, List.of(SummaryType.PREVIOUS, SummaryType.CURRENT, SummaryType.NEXT));
		}

		@Bean
		public TitleExtractor titleExtractor(OpenAiClient aiClient) {
			return new TitleExtractor(aiClient, 1);
		}

		@Bean
		public MetadataExtractor metadataExtractor(List<MetadataFeatureExtractor> extractors) {
			return new MetadataExtractor(extractors);
		}

	}

}
