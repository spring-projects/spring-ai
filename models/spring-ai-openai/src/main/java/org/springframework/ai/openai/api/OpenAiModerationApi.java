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

package org.springframework.ai.openai.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * OpenAI Moderation API.
 *
 * @author Ahmed Yousri
 * @see <a href=
 * "https://platform.openai.com/docs/api-reference/moderations">https://platform.openai.com/docs/api-reference/moderations</a>
 */
public class OpenAiModerationApi {

	public static final String DEFAULT_MODERATION_MODEL = "text-moderation-latest";

	private static final String DEFAULT_BASE_URL = "https://api.openai.com";

	private final RestClient restClient;

	private final ObjectMapper objectMapper;

	/**
	 * Create a new OpenAI Moderation api with base URL set to https://api.openai.com
	 * @param openAiToken OpenAI apiKey.
	 */
	public OpenAiModerationApi(String openAiToken) {
		this(DEFAULT_BASE_URL, openAiToken, RestClient.builder(), RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
	}

	public OpenAiModerationApi(String baseUrl, String openAiToken, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler) {

		this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		this.restClient = restClientBuilder.baseUrl(baseUrl).defaultHeaders(h -> {
			h.setBearerAuth(openAiToken);
			h.setContentType(MediaType.APPLICATION_JSON);
		}).defaultStatusHandler(responseErrorHandler).build();
	}

	public ResponseEntity<OpenAiModerationResponse> createModeration(OpenAiModerationRequest openAiModerationRequest) {
		Assert.notNull(openAiModerationRequest, "Moderation request cannot be null.");
		Assert.hasLength(openAiModerationRequest.prompt(), "Prompt cannot be empty.");

		return this.restClient.post()
			.uri("v1/moderations")
			.body(openAiModerationRequest)
			.retrieve()
			.toEntity(OpenAiModerationResponse.class);
	}

	// @formatter:off
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record OpenAiModerationRequest (
		@JsonProperty("input") String prompt,
		@JsonProperty("model") String model
	) {

		public OpenAiModerationRequest(String prompt) {
			this(prompt, null);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record OpenAiModerationResponse(
			@JsonProperty("id") String id,
			@JsonProperty("model") String model,
			@JsonProperty("results") OpenAiModerationResult[] results) {

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record OpenAiModerationResult(
			@JsonProperty("flagged") boolean flagged,
			@JsonProperty("categories") Categories categories,
			@JsonProperty("category_scores") CategoryScores categoryScores) {

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Categories(
			@JsonProperty("sexual") boolean sexual,
			@JsonProperty("hate") boolean hate,
			@JsonProperty("harassment") boolean harassment,
			@JsonProperty("self-harm") boolean selfHarm,
			@JsonProperty("sexual/minors") boolean sexualMinors,
			@JsonProperty("hate/threatening") boolean hateThreatening,
			@JsonProperty("violence/graphic") boolean violenceGraphic,
			@JsonProperty("self-harm/intent") boolean selfHarmIntent,
			@JsonProperty("self-harm/instructions") boolean selfHarmInstructions,
			@JsonProperty("harassment/threatening") boolean harassmentThreatening,
			@JsonProperty("violence") boolean violence) {

	}

	public record CategoryScores(
			@JsonProperty("sexual") double sexual,
			@JsonProperty("hate") double hate,
			@JsonProperty("harassment") double harassment,
			@JsonProperty("self-harm") double selfHarm,
			@JsonProperty("sexual/minors") double sexualMinors,
			@JsonProperty("hate/threatening") double hateThreatening,
			@JsonProperty("violence/graphic") double violenceGraphic,
			@JsonProperty("self-harm/intent") double selfHarmIntent,
			@JsonProperty("self-harm/instructions") double selfHarmInstructions,
			@JsonProperty("harassment/threatening") double harassmentThreatening,
			@JsonProperty("violence") double violence) {

	}
	// @formatter:onn

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Data(@JsonProperty("url") String url, @JsonProperty("b64_json") String b64Json,
			@JsonProperty("revised_prompt") String revisedPrompt) {

	}

}
