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

package org.springframework.ai.openai.moderation;

import java.util.List;

import org.hamcrest.core.StringContains;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.moderation.Categories;
import org.springframework.ai.moderation.CategoryScores;
import org.springframework.ai.moderation.Generation;
import org.springframework.ai.moderation.Moderation;
import org.springframework.ai.moderation.ModerationPrompt;
import org.springframework.ai.moderation.ModerationResponse;
import org.springframework.ai.moderation.ModerationResult;
import org.springframework.ai.openai.OpenAiModerationModel;
import org.springframework.ai.openai.api.OpenAiModerationApi;
import org.springframework.ai.openai.metadata.support.OpenAiApiResponseHeaders;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.restclient.test.autoconfigure.RestClientTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * @author Ahmed Yousri
 * @since 0.9.0
 */
@RestClientTest(OpenAiModerationModelTests.Config.class)
public class OpenAiModerationModelTests {

	private static String TEST_API_KEY = "sk-1234567890";

	@Autowired
	private OpenAiModerationModel openAiModerationModel;

	@Autowired
	private MockRestServiceServer server;

	@AfterEach
	void resetMockServer() {
		this.server.reset();
	}

	@Test
	void aiResponseContainsModerationResponseMetadata() {

		prepareMock();

		ModerationPrompt prompt = new ModerationPrompt("I want to kill them..");

		ModerationResponse response = this.openAiModerationModel.call(prompt);

		assertThat(response).isNotNull();
		Generation generation = response.getResult();
		assertThat(generation).isNotNull();

		Moderation moderation = response.getResult().getOutput();
		assertThat(moderation).isNotNull();
		assertThat(moderation.getId()).isEqualTo("modr-XXXXX");
		assertThat(moderation.getModel()).isEqualTo("text-moderation-005");

		List<ModerationResult> results = moderation.getResults();
		ModerationResult result = results.get(0);
		assertThat(result.isFlagged()).isTrue();

		// Assert Categories
		Categories categories = result.getCategories();
		assertThat(categories.isSexual()).isFalse();
		assertThat(categories.isHate()).isFalse();
		assertThat(categories.isHarassment()).isFalse();
		assertThat(categories.isSelfHarm()).isFalse();
		assertThat(categories.isSexualMinors()).isFalse();
		assertThat(categories.isHateThreatening()).isFalse();
		assertThat(categories.isViolenceGraphic()).isFalse();
		assertThat(categories.isSelfHarmIntent()).isFalse();
		assertThat(categories.isSelfHarmInstructions()).isFalse();
		assertThat(categories.isHarassmentThreatening()).isTrue();
		assertThat(categories.isViolence()).isTrue();

		// Assert CategoryScores
		CategoryScores scores = result.getCategoryScores();
		assertThat(scores.getSexual()).isEqualTo(1.2282071E-6);
		assertThat(scores.getHate()).isEqualTo(0.010696256);
		assertThat(scores.getHarassment()).isEqualTo(0.29842457);
		assertThat(scores.getSelfHarm()).isEqualTo(1.5236925E-8);
		assertThat(scores.getSexualMinors()).isEqualTo(5.7246268E-8);
		assertThat(scores.getHateThreatening()).isEqualTo(0.0060676364);
		assertThat(scores.getViolenceGraphic()).isEqualTo(4.435014E-6);
		assertThat(scores.getSelfHarmIntent()).isEqualTo(8.098441E-10);
		assertThat(scores.getSelfHarmInstructions()).isEqualTo(2.8498655E-11);
		assertThat(scores.getHarassmentThreatening()).isEqualTo(0.63055265);
		assertThat(scores.getViolence()).isEqualTo(0.99011886);

	}

	private void prepareMock() {

		HttpHeaders httpHeaders = new HttpHeaders();
		httpHeaders.set(OpenAiApiResponseHeaders.REQUESTS_LIMIT_HEADER.getName(), "4000");
		httpHeaders.set(OpenAiApiResponseHeaders.REQUESTS_REMAINING_HEADER.getName(), "999");
		httpHeaders.set(OpenAiApiResponseHeaders.REQUESTS_RESET_HEADER.getName(), "2d16h15m29s");
		httpHeaders.set(OpenAiApiResponseHeaders.TOKENS_LIMIT_HEADER.getName(), "725000");
		httpHeaders.set(OpenAiApiResponseHeaders.TOKENS_REMAINING_HEADER.getName(), "112358");
		httpHeaders.set(OpenAiApiResponseHeaders.TOKENS_RESET_HEADER.getName(), "27h55s451ms");

		this.server.expect(requestTo(StringContains.containsString("v1/moderations")))
			.andExpect(method(HttpMethod.POST))
			.andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer " + TEST_API_KEY))
			.andRespond(withSuccess(getJson(), MediaType.APPLICATION_JSON).headers(httpHeaders));

	}

	private String getJson() {
		return """
					{
						"id": "modr-XXXXX",
						"model": "text-moderation-005",
						"results": [
							{
								"flagged": true,
								"categories": {
								"sexual": false,
								"hate": false,
								"harassment": false,
								"self-harm": false,
								"sexual/minors": false,
								"hate/threatening": false,
								"violence/graphic": false,
								"self-harm/intent": false,
								"self-harm/instructions": false,
								"harassment/threatening": true,
								"violence": true
							},
							"category_scores": {
								"sexual": 1.2282071e-06,
								"hate": 0.010696256,
								"harassment": 0.29842457,
								"self-harm": 1.5236925e-08,
								"sexual/minors": 5.7246268e-08,
								"hate/threatening": 0.0060676364,
								"violence/graphic": 4.435014e-06,
								"self-harm/intent": 8.098441e-10,
								"self-harm/instructions": 2.8498655e-11,
								"harassment/threatening": 0.63055265,
								"violence": 0.99011886
							}
						}
					]
				}
				""";
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public OpenAiModerationApi moderationGenerationApi(RestClient.Builder builder) {
			return OpenAiModerationApi.builder()
				.apiKey(new SimpleApiKey(TEST_API_KEY))
				.restClientBuilder(builder)
				.responseErrorHandler(RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER)
				.build();
		}

		@Bean
		public OpenAiModerationModel openAiModerationClient(OpenAiModerationApi openAiModerationApi) {
			return new OpenAiModerationModel(openAiModerationApi);
		}

	}

}
