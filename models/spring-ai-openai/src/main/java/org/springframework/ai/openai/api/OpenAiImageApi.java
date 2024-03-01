/*
 * Copyright 2024-2024 the original author or authors.
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

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.openai.api.common.ApiUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.RestClient;

/**
 * OpenAI Image API.
 *
 * @see <a href=
 * "https://platform.openai.com/docs/api-reference/images">https://platform.openai.com/docs/api-reference/images</a>
 */
public class OpenAiImageApi {

	private static final String DEFAULT_BASE_URL = "https://api.openai.com";

	public static final String DEFAULT_IMAGE_MODEL = "dall-e-2";

	private final RestClient restClient;

	/**
	 * Create a new OpenAI Image api with base URL set to https://api.openai.com
	 * @param openAiToken OpenAI apiKey.
	 */
	public OpenAiImageApi(String openAiToken) {
		this(DEFAULT_BASE_URL, openAiToken, RestClient.builder());
	}

	public OpenAiImageApi(String baseUrl, String openAiToken, RestClient.Builder restClientBuilder) {

		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(ApiUtils.getJsonContentHeaders(openAiToken))
			.defaultStatusHandler(ApiUtils.DEFAULT_RESPONSE_ERROR_HANDLER)
			.build();
	}

	/**
	 * OpenAI Image API model.
	 * <a href="https://platform.openai.com/docs/models/dall-e">DALL·E</a>
	 */
	enum ImageModel {

		/**
		 * The latest DALL·E model released in Nov 2023.
		 */
		DALL_E_3("dall-e-3"),

		/**
		 * The previous DALL·E model released in Nov 2022. The 2nd iteration of DALL·E
		 * with more realistic, accurate, and 4x greater resolution images than the
		 * original model.
		 */
		DALL_E_2("dall-e-2");

		private final String model;

		ImageModel(String model) {
			this.model = model;
		}

		public String model() {
			return this.model;
		}

	}

	// @formatter:off
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record OpenAiImageRequest (
		@JsonProperty("prompt") String prompt,
		@JsonProperty("model") String model,
		@JsonProperty("n") Integer n,
		@JsonProperty("quality") String quality,
		@JsonProperty("response_format") String responseFormat,
		@JsonProperty("size") String size,
		@JsonProperty("style") String style,
		@JsonProperty("user") String user) {

		public OpenAiImageRequest(String prompt, String model) {
			this(prompt, model, null, null, null, null, null, null);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record OpenAiImageResponse(
		@JsonProperty("created") Long created,
		@JsonProperty("data") List<Data> data) {
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Data(
		@JsonProperty("url") String url,
		@JsonProperty("b64_json") String b64Json,
		@JsonProperty("revised_prompt") String revisedPrompt) {
	}
	// @formatter:onn

	public ResponseEntity<OpenAiImageResponse> createImage(OpenAiImageRequest openAiImageRequest) {
		Assert.notNull(openAiImageRequest, "Image request cannot be null.");
		Assert.hasLength(openAiImageRequest.prompt(), "Prompt cannot be empty.");

		return this.restClient.post()
			.uri("v1/images/generations")
			.body(openAiImageRequest)
			.retrieve()
			.toEntity(OpenAiImageResponse.class);
	}

}
