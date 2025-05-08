/*
 * Copyright 2023-2025 the original author or authors.
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

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.model.ApiKey;
import org.springframework.ai.model.NoopApiKey;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.api.common.OpenAiApiConstants;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * OpenAI Moderation API.
 *
 * @author Ahmed Yousri
 * @author Ilayaperumal Gopinathan
 * @see <a href=
 * "https://platform.openai.com/docs/api-reference/moderations">https://platform.openai.com/docs/api-reference/moderations</a>
 */
public class OpenAiModerationApi {

	public static final String DEFAULT_MODERATION_MODEL = "text-moderation-latest";

	private static final String DEFAULT_BASE_URL = "https://api.openai.com";

	private final RestClient restClient;

	private final ObjectMapper objectMapper;

	/**
	 * Create a new OpenAI Moderation API with the provided base URL.
	 * @param baseUrl the base URL for the OpenAI API.
	 * @param apiKey OpenAI apiKey.
	 * @param restClientBuilder the rest client builder to use.
	 */
	public OpenAiModerationApi(String baseUrl, ApiKey apiKey, MultiValueMap<String, String> headers,
			RestClient.Builder restClientBuilder, ResponseErrorHandler responseErrorHandler) {

		this.objectMapper = new ObjectMapper().configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

		this.restClient = restClientBuilder.baseUrl(baseUrl).defaultHeaders(h -> {
			if (!(apiKey instanceof NoopApiKey)) {
				h.setBearerAuth(apiKey.getValue());
			}
			h.setContentType(MediaType.APPLICATION_JSON);
			h.addAll(headers);
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

	public static Builder builder() {
		return new Builder();
	}

	// @formatter:off
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record OpenAiModerationRequest(
		@JsonProperty("input") String prompt,
		@JsonProperty("model") String model
	) {

		public OpenAiModerationRequest(String prompt) {
			this(prompt, null);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record OpenAiModerationResponse(
			@JsonProperty("id") String id,
			@JsonProperty("model") String model,
			@JsonProperty("results") OpenAiModerationResult[] results) {

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record OpenAiModerationResult(
			@JsonProperty("flagged") boolean flagged,
			@JsonProperty("categories") Categories categories,
			@JsonProperty("category_scores") CategoryScores categoryScores) {

	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
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

	@JsonInclude(JsonInclude.Include.NON_NULL)
	@JsonIgnoreProperties(ignoreUnknown = true)
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
	@JsonIgnoreProperties(ignoreUnknown = true)
	public record Data(@JsonProperty("url") String url, @JsonProperty("b64_json") String b64Json,
			@JsonProperty("revised_prompt") String revisedPrompt) {

	}

	/**
	 * Builder to construct {@link OpenAiModerationApi} instance.
	 */
	public static class Builder {

		private String baseUrl = OpenAiApiConstants.DEFAULT_BASE_URL;

		private ApiKey apiKey;

		private MultiValueMap<String, String> headers = new LinkedMultiValueMap<>();

		private RestClient.Builder restClientBuilder = RestClient.builder();

		private ResponseErrorHandler responseErrorHandler = RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER;

		public Builder baseUrl(String baseUrl) {
			Assert.hasText(baseUrl, "baseUrl cannot be null or empty");
			this.baseUrl = baseUrl;
			return this;
		}

		public Builder apiKey(ApiKey apiKey) {
			Assert.notNull(apiKey, "apiKey cannot be null");
			this.apiKey = apiKey;
			return this;
		}

		public Builder apiKey(String simpleApiKey) {
			Assert.notNull(simpleApiKey, "simpleApiKey cannot be null");
			this.apiKey = new SimpleApiKey(simpleApiKey);
			return this;
		}

		public Builder headers(MultiValueMap<String, String> headers) {
			Assert.notNull(headers, "headers cannot be null");
			this.headers = headers;
			return this;
		}

		public Builder restClientBuilder(RestClient.Builder restClientBuilder) {
			Assert.notNull(restClientBuilder, "restClientBuilder cannot be null");
			this.restClientBuilder = restClientBuilder;
			return this;
		}

		public Builder responseErrorHandler(ResponseErrorHandler responseErrorHandler) {
			Assert.notNull(responseErrorHandler, "responseErrorHandler cannot be null");
			this.responseErrorHandler = responseErrorHandler;
			return this;
		}

		public OpenAiModerationApi build() {
			Assert.notNull(this.apiKey, "apiKey must be set");
			return new OpenAiModerationApi(this.baseUrl, this.apiKey, this.headers, this.restClientBuilder,
					this.responseErrorHandler);
		}

	}

}
