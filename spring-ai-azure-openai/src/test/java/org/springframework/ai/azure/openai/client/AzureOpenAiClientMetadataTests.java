/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.azure.openai.client;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.ai.test.config.MockAiTestConfiguration.SPRING_AI_API_PATH;

import java.nio.charset.StandardCharsets;

import com.azure.ai.openai.models.ContentFilterResult;
import com.azure.ai.openai.models.ContentFilterResults;
import com.azure.ai.openai.models.ContentFilterSeverity;

import org.junit.jupiter.api.Test;

import org.springframework.ai.azure.openai.MockAzureOpenAiTestConfiguration;
import org.springframework.ai.client.AiResponse;
import org.springframework.ai.client.Generation;
import org.springframework.ai.metadata.ChoiceMetadata;
import org.springframework.ai.metadata.GenerationMetadata;
import org.springframework.ai.metadata.PromptMetadata;
import org.springframework.ai.metadata.RateLimit;
import org.springframework.ai.metadata.Usage;
import org.springframework.ai.prompt.Prompt;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.context.request.WebRequest;

/**
 * Unit Tests for {@link AzureOpenAiClient} asserting AI metadata.
 *
 * @author John Blum
 * @since 0.7.0
 */
@SpringBootTest
@ActiveProfiles("spring-ai-azure-openai-mocks")
@ContextConfiguration(classes = AzureOpenAiClientMetadataTests.TestConfiguration.class)
@SuppressWarnings("unused")
class AzureOpenAiClientMetadataTests {

	@Autowired
	private AzureOpenAiClient aiClient;

	@Test
	void azureOpenAiMetadataCapturedDuringGeneration() {

		Prompt prompt = new Prompt("Can I fly like a bird?");

		AiResponse response = this.aiClient.generate(prompt);

		assertThat(response).isNotNull();

		Generation generation = response.getGeneration();

		assertThat(generation).isNotNull()
			.extracting(Generation::getText)
			.isEqualTo("No! You will actually land with a resounding thud. This is the way!");

		assertPromptMetadata(response);
		assertGenerationMetadata(response);
		assertChoiceMetadata(generation);
	}

	private void assertPromptMetadata(AiResponse response) {

		PromptMetadata promptMetadata = response.getPromptMetadata();

		assertThat(promptMetadata).isNotNull();

		PromptMetadata.PromptFilterMetadata promptFilterMetadata = promptMetadata.findByPromptIndex(0).orElse(null);

		assertThat(promptFilterMetadata).isNotNull();
		assertThat(promptFilterMetadata.getPromptIndex()).isZero();
		assertContentFilterResults(promptFilterMetadata.getContentFilterMetadata(), ContentFilterSeverity.HIGH);
	}

	private void assertGenerationMetadata(AiResponse response) {

		GenerationMetadata generationMetadata = response.getGenerationMetadata();

		assertThat(generationMetadata).isNotNull();
		assertThat(generationMetadata.getRateLimit()).isEqualTo(RateLimit.NULL);

		Usage usage = generationMetadata.getUsage();

		assertThat(usage).isNotNull();
		assertThat(usage).isNotEqualTo(Usage.NULL);
		assertThat(usage.getPromptTokens()).isEqualTo(58);
		assertThat(usage.getGenerationTokens()).isEqualTo(68);
		assertThat(usage.getTotalTokens()).isEqualTo(126);
	}

	private void assertChoiceMetadata(Generation generation) {

		ChoiceMetadata choiceMetadata = generation.getChoiceMetadata();

		assertThat(choiceMetadata).isNotNull();
		assertThat(choiceMetadata.getFinishReason()).isEqualTo("stop");
		assertContentFilterResults(choiceMetadata.getContentFilterMetadata());
	}

	private void assertContentFilterResults(ContentFilterResults contentFilterResults) {
		assertContentFilterResults(contentFilterResults, ContentFilterSeverity.SAFE);
	}

	private void assertContentFilterResults(ContentFilterResults contentFilterResults,
			ContentFilterSeverity selfHarmSeverity) {

		assertThat(contentFilterResults).isNotNull();
		assertContentFilterResult(contentFilterResults.getHate());
		assertContentFilterResult(contentFilterResults.getSelfHarm(), selfHarmSeverity);
		assertContentFilterResult(contentFilterResults.getSexual());
		assertContentFilterResult(contentFilterResults.getViolence());
	}

	private void assertContentFilterResult(ContentFilterResult contentFilterResult) {

		assertThat(contentFilterResult).isNotNull();
		assertContentFilterResult(contentFilterResult, contentFilterResult.getSeverity());
	}

	private void assertContentFilterResult(ContentFilterResult contentFilterResult,
			ContentFilterSeverity expectedSeverity) {

		boolean filtered = !ContentFilterSeverity.SAFE.equals(expectedSeverity);

		assertThat(contentFilterResult).isNotNull();
		assertThat(contentFilterResult.isFiltered()).isEqualTo(filtered);
		assertThat(contentFilterResult.getSeverity()).isEqualTo(expectedSeverity);
	}

	@SpringBootConfiguration
	@Profile("spring-ai-azure-openai-mocks")
	@Import(MockAzureOpenAiTestConfiguration.class)
	static class TestConfiguration {

		@Bean
		MockMvc mockMvc() {
			return MockMvcBuilders.standaloneSetup(new SpringAzureOpenAiChatCompletionsController()).build();
		}

	}

	@RestController
	@RequestMapping(SPRING_AI_API_PATH)
	@SuppressWarnings("all")
	static class SpringAzureOpenAiChatCompletionsController {

		@PostMapping("/openai/deployments/gpt-35-turbo/chat/completions")
		ResponseEntity<?> chatCompletions(WebRequest request) {

			String json = getJson();

			ResponseEntity<?> response = ResponseEntity.status(HttpStatusCode.valueOf(200))
				.contentType(MediaType.APPLICATION_JSON)
				.contentLength(json.getBytes(StandardCharsets.UTF_8).length)
				.body(getJson());

			return response;
		}

		private String getJson() {
			return """
						{
							"id": "chatcmpl-6v7mkQj980V1yBec6ETrKPRqFjNw9",
							"object": "chat.completion",
							"created": 1679072642,
							"model": "gpt-35-turbo",
							"choices":[{
								"index": 0,
								"content_filter_results" : {
									"error" : null,
									"hate" : {
										"filtered" : false,
										"severity" : "safe"
									},
									"self_harm" : {
										"filtered" : false,
										"severity" : "safe"
									},
									"sexual" : {
										"filtered" : false,
										"severity" : "safe"
									},
									"violence" : {
										"filtered" : false,
										"severity" : "safe"
									}
								},
								"finish_reason": "stop",
								"message":{
									"role": "user",
									"content": "No! You will actually land with a resounding thud. This is the way!"
								}
							}],
							"usage":{
								"prompt_tokens":58,
								"completion_tokens":68,
								"total_tokens":126
							},
							"prompt_annotations" : [{
								"prompt_index" : 0,
								"content_filter_results" : {
										"error" : null,
										"hate" : {
											"filtered" : false,
											"severity" : "safe"
										},
										"self_harm" : {
											"filtered" : true,
											"severity" : "high"
										},
										"sexual" : {
											"filtered" : false,
											"severity" : "safe"
										},
										"violence" : {
											"filtered" : false,
											"severity" : "safe"
										}
									}
							}]
						}
					""";
		}

	}

}
