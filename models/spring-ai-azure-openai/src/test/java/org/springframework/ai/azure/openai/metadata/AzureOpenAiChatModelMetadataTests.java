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

package org.springframework.ai.azure.openai.metadata;

import java.nio.charset.StandardCharsets;

import com.azure.ai.openai.models.*;
import org.junit.jupiter.api.Test;

import org.springframework.ai.azure.openai.AzureOpenAiChatModel;
import org.springframework.ai.azure.openai.MockAzureOpenAiTestConfiguration;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.EmptyRateLimit;
import org.springframework.ai.chat.metadata.PromptMetadata;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
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

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link AzureOpenAiChatModel} asserting AI metadata.
 *
 * @author John Blum
 * @author Christian Tzolov
 * @since 0.7.0
 */
@SpringBootTest
@ActiveProfiles("spring-ai-azure-openai-mocks")
@ContextConfiguration(classes = AzureOpenAiChatModelMetadataTests.TestConfiguration.class)
@SuppressWarnings("unused")
class AzureOpenAiChatModelMetadataTests {

	@Autowired
	private AzureOpenAiChatModel aiClient;

	@Test
	void azureOpenAiMetadataCapturedDuringGeneration() {

		Prompt prompt = new Prompt("Can I fly like a bird?");

		ChatResponse response = this.aiClient.call(prompt);

		assertThat(response).isNotNull();

		Generation generation = response.getResult();

		assertThat(generation).isNotNull()
			.extracting(Generation::getOutput)
			.extracting(AssistantMessage::getText)
			.isEqualTo("No! You will actually land with a resounding thud. This is the way!");

		// assertPromptMetadata(response);
		assertGenerationMetadata(response);
		assertChoiceMetadata(generation);
	}

	private void assertPromptMetadata(ChatResponse response) {

		PromptMetadata promptMetadata = response.getMetadata().getPromptMetadata();

		assertThat(promptMetadata).isNotNull();

		PromptMetadata.PromptFilterMetadata promptFilterMetadata = promptMetadata.findByPromptIndex(0).orElse(null);

		assertThat(promptFilterMetadata).isNotNull();
		assertThat(promptFilterMetadata.getPromptIndex()).isZero();
		assertContentFilterResultsForPrompt(promptFilterMetadata.getContentFilterMetadata(),
				ContentFilterSeverity.HIGH);
	}

	private void assertGenerationMetadata(ChatResponse response) {

		ChatResponseMetadata chatResponseMetadata = response.getMetadata();

		assertThat(chatResponseMetadata).isNotNull();
		assertThat(chatResponseMetadata.getRateLimit().getRequestsLimit())
			.isEqualTo(new EmptyRateLimit().getRequestsLimit());

		Usage usage = chatResponseMetadata.getUsage();

		assertThat(usage).isNotNull();
		assertThat(usage.getPromptTokens()).isEqualTo(58);
		assertThat(usage.getCompletionTokens()).isEqualTo(68);
		assertThat(usage.getTotalTokens()).isEqualTo(126);
	}

	private void assertChoiceMetadata(Generation generation) {

		ChatGenerationMetadata chatGenerationMetadata = generation.getMetadata();

		assertThat(chatGenerationMetadata).isNotNull();
		assertThat(chatGenerationMetadata.getFinishReason()).isEqualTo("stop");
		assertContentFilterResults(chatGenerationMetadata.get("contentFilterResults"));
		assertLogprobs(chatGenerationMetadata.get("logprobs"));
	}

	private static void assertLogprobs(ChatChoiceLogProbabilityInfo logprobsInfo) {
		assertThat(logprobsInfo.getContent()).hasSize(9);
		assertLogprobResult(logprobsInfo.getContent().get(0), -0.0009114635, "Hello", 72, 101, 108, 108, 111);
		assertThat(logprobsInfo.getContent().get(0).getTopLogprobs()).hasSize(3);

		assertLogprobResult(logprobsInfo.getContent().get(1), -0.0000019816675, "!", 33);
		assertThat(logprobsInfo.getContent().get(1).getTopLogprobs()).hasSize(3);

		assertLogprobResult(logprobsInfo.getContent().get(2), -3.1281633e-7, " How", 32, 72, 111, 119);
		assertThat(logprobsInfo.getContent().get(2).getTopLogprobs()).hasSize(3);

		assertLogprobResult(logprobsInfo.getContent().get(3), -0.0000079418505, " can", 32, 99, 97, 110);
		assertThat(logprobsInfo.getContent().get(3).getTopLogprobs()).hasSize(3);

		assertLogprobResult(logprobsInfo.getContent().get(4), 0, " I", 32, 73);
		assertThat(logprobsInfo.getContent().get(4).getTopLogprobs()).hasSize(3);

		assertLogprobResult(logprobsInfo.getContent().get(5), -0.0010328111, " assist", 32, 97, 115, 115, 105, 115,
				116);
		assertThat(logprobsInfo.getContent().get(5).getTopLogprobs()).hasSize(3);

		assertLogprobResult(logprobsInfo.getContent().get(6), 0, " you", 32, 121, 111, 117);
		assertThat(logprobsInfo.getContent().get(6).getTopLogprobs()).hasSize(3);

		assertLogprobResult(logprobsInfo.getContent().get(7), 0, " today", 32, 116, 111, 100, 97, 121);
		assertThat(logprobsInfo.getContent().get(7).getTopLogprobs()).hasSize(3);

		assertLogprobResult(logprobsInfo.getContent().get(8), -0.0000023392786, "?", 63);
		assertThat(logprobsInfo.getContent().get(8).getTopLogprobs()).hasSize(3);

		assertLogprobInfo(logprobsInfo.getContent().get(0).getTopLogprobs().get(0), -0.0009114635, "Hello", 72, 101,
				108, 108, 111);
		assertLogprobInfo(logprobsInfo.getContent().get(0).getTopLogprobs().get(1), -7.000911, "Hi", 72, 105);
		assertLogprobInfo(logprobsInfo.getContent().get(0).getTopLogprobs().get(2), -19.875912, "Hey", 72, 101, 121);

	}

	private static void assertLogprobResult(ChatTokenLogProbabilityResult actual, double expectedLogprob,
			String expectedToken, Integer... expectedBytes) {
		assertThat(actual.getLogprob()).isEqualTo(expectedLogprob);
		assertThat(actual.getBytes()).contains(expectedBytes);
		assertThat(actual.getToken()).isEqualTo(expectedToken);
	}

	private static void assertLogprobInfo(ChatTokenLogProbabilityInfo actual, double expectedLogprob,
			String expectedToken, Integer... expectedBytes) {
		assertThat(actual.getLogprob()).isEqualTo(expectedLogprob);
		assertThat(actual.getBytes()).contains(expectedBytes);
		assertThat(actual.getToken()).isEqualTo(expectedToken);
	}

	private void assertContentFilterResultsForPrompt(ContentFilterResultDetailsForPrompt contentFilterResultForPrompt,
			ContentFilterSeverity selfHarmSeverity) {

		assertThat(contentFilterResultForPrompt).isNotNull();
		assertContentFilterResult(contentFilterResultForPrompt.getHate());
		assertContentFilterResult(contentFilterResultForPrompt.getSelfHarm(), selfHarmSeverity);
		assertContentFilterResult(contentFilterResultForPrompt.getSexual());
		assertContentFilterResult(contentFilterResultForPrompt.getViolence());

	}

	private void assertContentFilterResults(ContentFilterResultsForChoice contentFilterResults) {
		assertContentFilterResults(contentFilterResults, ContentFilterSeverity.SAFE);
	}

	private void assertContentFilterResults(ContentFilterResultsForChoice contentFilterResults,
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
	@RequestMapping("/spring-ai/api")
	@SuppressWarnings("all")
	static class SpringAzureOpenAiChatCompletionsController {

		@PostMapping("/openai/deployments/gpt-4o/chat/completions")
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
							"model": "gpt-4o",
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
								"index": 0,
								"logprobs": {
								   "content": [
									 {
									   "bytes": [
										 72,
										 101,
										 108,
										 108,
										 111
									   ],
									   "logprob": -0.0009114635,
									   "token": "Hello",
									   "top_logprobs": [
										 {
										   "bytes": [
											 72,
											 101,
											 108,
											 108,
											 111
										   ],
										   "logprob": -0.0009114635,
										   "token": "Hello"
										 },
										 {
										   "bytes": [
											 72,
											 105
										   ],
										   "logprob": -7.000911,
										   "token": "Hi"
										 },
										 {
										   "bytes": [
											 72,
											 101,
											 121
										   ],
										   "logprob": -19.875912,
										   "token": "Hey"
										 }
									   ]
									 },
									 {
									   "bytes": [
										 33
									   ],
									   "logprob": -0.0000019816675,
									   "token": "!",
									   "top_logprobs": [
										 {
										   "bytes": [
											 33
										   ],
										   "logprob": -0.0000019816675,
										   "token": "!"
										 },
										 {
										   "bytes": [
											 32,
											 116,
											 104,
											 101,
											 114,
											 101
										   ],
										   "logprob": -13.187502,
										   "token": " there"
										 },
										 {
										   "bytes": [
											 46
										   ],
										   "logprob": -20.687502,
										   "token": "."
										 }
									   ]
									 },
									 {
									   "bytes": [
										 32,
										 72,
										 111,
										 119
									   ],
									   "logprob": -3.1281633e-7,
									   "token": " How",
									   "top_logprobs": [
										 {
										   "bytes": [
											 32,
											 72,
											 111,
											 119
										   ],
										   "logprob": -3.1281633e-7,
										   "token": " How"
										 },
										 {
										   "bytes": [
											 32,
											 87,
											 104,
											 97,
											 116
										   ],
										   "logprob": -15.125,
										   "token": " What"
										 },
										 {
										   "bytes": [
											 32,
											 104,
											 111,
											 119
										   ],
										   "logprob": -20.75,
										   "token": " how"
										 }
									   ]
									 },
									 {
									   "bytes": [
										 32,
										 99,
										 97,
										 110
									   ],
									   "logprob": -0.0000079418505,
									   "token": " can",
									   "top_logprobs": [
										 {
										   "bytes": [
											 32,
											 99,
											 97,
											 110
										   ],
										   "logprob": -0.0000079418505,
										   "token": " can"
										 },
										 {
										   "bytes": [
											 32,
											 109,
											 97,
											 121
										   ],
										   "logprob": -11.750008,
										   "token": " may"
										 },
										 {
										   "bytes": [
											 32,
											 109,
											 105,
											 103,
											 104,
											 116
										   ],
										   "logprob": -21.250008,
										   "token": " might"
										 }
									   ]
									 },
									 {
									   "bytes": [
										 32,
										 73
									   ],
									   "logprob": 0,
									   "token": " I",
									   "top_logprobs": [
										 {
										   "bytes": [
											 32,
											 73
										   ],
										   "logprob": 0,
										   "token": " I"
										 },
										 {
										   "bytes": [
											 32,
											 97,
											 115,
											 115,
											 105,
											 115,
											 116
										   ],
										   "logprob": -24.75,
										   "token": " assist"
										 },
										 {
										   "bytes": [
											 73
										   ],
										   "logprob": -25.875,
										   "token": "I"
										 }
									   ]
									 },
									 {
									   "bytes": [
										 32,
										 97,
										 115,
										 115,
										 105,
										 115,
										 116
									   ],
									   "logprob": -0.0010328111,
									   "token": " assist",
									   "top_logprobs": [
										 {
										   "bytes": [
											 32,
											 97,
											 115,
											 115,
											 105,
											 115,
											 116
										   ],
										   "logprob": -0.0010328111,
										   "token": " assist"
										 },
										 {
										   "bytes": [
											 32,
											 104,
											 101,
											 108,
											 112
										   ],
										   "logprob": -6.876033,
										   "token": " help"
										 },
										 {
										   "bytes": [
											 97,
											 115,
											 115,
											 105,
											 115,
											 116
										   ],
										   "logprob": -18.251032,
										   "token": "assist"
										 }
									   ]
									 },
									 {
									   "bytes": [
										 32,
										 121,
										 111,
										 117
									   ],
									   "logprob": 0,
									   "token": " you",
									   "top_logprobs": [
										 {
										   "bytes": [
											 32,
											 121,
											 111,
											 117
										   ],
										   "logprob": 0,
										   "token": " you"
										 },
										 {
										   "bytes": [
											 32,
											 118,
											 111,
											 99,
											 195,
											 170
										   ],
										   "logprob": -26.625,
										   "token": " você"
										 },
										 {
										   "bytes": [
											 121,
											 111,
											 117
										   ],
										   "logprob": -26.75,
										   "token": "you"
										 }
									   ]
									 },
									 {
									   "bytes": [
										 32,
										 116,
										 111,
										 100,
										 97,
										 121
									   ],
									   "logprob": 0,
									   "token": " today",
									   "top_logprobs": [
										 {
										   "bytes": [
											 32,
											 116,
											 111,
											 100,
											 97,
											 121
										   ],
										   "logprob": 0,
										   "token": " today"
										 },
										 {
										   "bytes": [
											 63
										   ],
										   "logprob": -21.375,
										   "token": "?"
										 },
										 {
										   "bytes": [
											 32,
											 116,
											 111,
											 100,
											 97
										   ],
										   "logprob": -25.25,
										   "token": " toda"
										 }
									   ]
									 },
									 {
									   "bytes": [
										 63
									   ],
									   "logprob": -0.0000023392786,
									   "token": "?",
									   "top_logprobs": [
										 {
										   "bytes": [
											 63
										   ],
										   "logprob": -0.0000023392786,
										   "token": "?"
										 },
										 {
										   "bytes": [
											 63,
											 10
										   ],
										   "logprob": -13.000002,
										   "token": "?\\n"
										 },
										 {
										   "bytes": [
											 63,
											 10,
											 10
										   ],
										   "logprob": -16.750002,
										   "token": "?\\n\\n"
										 }
									   ]
									 }
								   ],
								   "refusal": null
								},
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
							"prompt_filter_results" : [{
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
