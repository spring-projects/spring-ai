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

package org.springframework.ai.qianfan.api;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

import org.springframework.ai.qianfan.api.auth.AuthApi;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * QianFan Image API.
 *
 * @author Geng Rong
 * @since 1.0
 */
public class QianFanImageApi extends AuthApi {

	public static final String DEFAULT_IMAGE_MODEL = ImageModel.Stable_Diffusion_XL.getValue();

	private final RestClient restClient;

	/**
	 * Create a new QianFan Image api with default base URL.
	 * @param apiKey QianFan api key.
	 * @param secretKey QianFan secret key.
	 */
	public QianFanImageApi(String apiKey, String secretKey) {
		this(QianFanConstants.DEFAULT_BASE_URL, apiKey, secretKey, RestClient.builder());
	}

	/**
	 * Create a new QianFan Image API with the provided base URL.
	 * @param baseUrl the base URL for the QianFan API.
	 * @param apiKey QianFan api key.
	 * @param secretKey QianFan secret key.
	 * @param restClientBuilder the rest client builder to use.
	 */
	public QianFanImageApi(String baseUrl, String apiKey, String secretKey, RestClient.Builder restClientBuilder) {
		this(baseUrl, apiKey, secretKey, restClientBuilder, RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
	}

	/**
	 * Create a new QianFan Image API with the provided base URL.
	 * @param baseUrl the base URL for the QianFan API.
	 * @param apiKey QianFan api key.
	 * @param secretKey QianFan secret key.
	 * @param restClientBuilder the rest client builder to use.
	 * @param responseErrorHandler the response error handler to use.
	 */
	public QianFanImageApi(String baseUrl, String apiKey, String secretKey, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler) {
		super(apiKey, secretKey);

		this.restClient = restClientBuilder.baseUrl(baseUrl)
			.defaultHeaders(QianFanUtils.defaultHeaders())
			.defaultStatusHandler(responseErrorHandler)
			.build();
	}

	public ResponseEntity<QianFanImageResponse> createImage(QianFanImageRequest qianFanImageRequest) {
		Assert.notNull(qianFanImageRequest, "Image request cannot be null.");
		Assert.hasLength(qianFanImageRequest.prompt(), "Prompt cannot be empty.");

		return this.restClient.post()
			.uri("/v1/wenxinworkshop/text2image/{model}?access_token={token}", qianFanImageRequest.model(),
					getAccessToken())
			.body(qianFanImageRequest)
			.retrieve()
			.toEntity(QianFanImageResponse.class);
	}

	/**
	 * QianFan Image API model.
	 */
	public enum ImageModel {

		/**
		 * Stable Diffusion XL (SDXL) is a powerful text-to-image generation model.
		 */
		Stable_Diffusion_XL("sd_xl");

		private final String value;

		ImageModel(String model) {
			this.value = model;
		}

		public String getValue() {
			return this.value;
		}

	}

	// @formatter:off
	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record QianFanImageRequest (
		@JsonProperty("model") String model,
		@JsonProperty("prompt") String prompt,
		@JsonProperty("negative_prompt") String negativePrompt,
		@JsonProperty("size") String size,
		@JsonProperty("n") Integer n,
		@JsonProperty("steps") Integer steps,
		@JsonProperty("seed") Integer seed,
		@JsonProperty("style") String style,
		@JsonProperty("user_id") String user) {

		public QianFanImageRequest(String prompt, String model) {
			this(model, prompt, null, null, null, null, null, null, null);
		}
	}

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record QianFanImageResponse(
		@JsonProperty("id") String id,
		@JsonProperty("created") Long created,
		@JsonProperty("data") List<Data> data) {
	}
	// @formatter:onn

	@JsonInclude(JsonInclude.Include.NON_NULL)
	public record Data(@JsonProperty("index") Integer index, @JsonProperty("b64_image") String b64Image) {

	}

}
