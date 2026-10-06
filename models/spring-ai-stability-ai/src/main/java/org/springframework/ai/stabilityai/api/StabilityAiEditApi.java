/*
 * Copyright 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.stabilityai.api;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import java.util.List;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.stabilityai.api.StabilityAiEditApi.ImageEditHeaders.AcceptType;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;

/**
 * Edit image using
 * <a href="https://platform.stability.ai/docs/api-reference#tag/Edit">StabilityAI Image
 * Edit (V2 Beta) API</a>
 *
 * @author inpink
 */
public class StabilityAiEditApi {

	private final RestClient restClient;

	/**
	 * Create a new StabilityAI Edit API.
	 * @param apiKey StabilityAI apiKey.
	 */
	public StabilityAiEditApi(String apiKey) {
		this(apiKey, "https://api.stability.ai/v2beta/stable-image/edit", RestClient.builder(),
				RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
	}

	/**
	 * Create a new StabilityAI Edit API.
	 * @param apiKey StabilityAI apiKey.
	 * @param baseUrl api base URL.
	 * @param restClientBuilder RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public StabilityAiEditApi(String apiKey, String baseUrl, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler) {
		this(apiKey, baseUrl, restClientBuilder, responseErrorHandler, ImageEditHeaders.builder().build());
	}

	/**
	 * Create a new StabilityAI Edit API.
	 * @param apiKey StabilityAI apiKey.
	 * @param baseUrl api base URL.
	 * @param restClientBuilder RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 * @param headers headers.
	 */
	public StabilityAiEditApi(String apiKey, String baseUrl, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler, ImageEditHeaders headers) {
		this.restClient = restClientBuilder.baseUrl(baseUrl).defaultHeaders(h -> {
			h.setBearerAuth(apiKey);
			h.setContentType(MediaType.MULTIPART_FORM_DATA);
			h.setAccept(List.of(AcceptType.IMAGE_ALL.mediaType));
			h.addAll(headers.toHttpHeaders());
		}).defaultStatusHandler(responseErrorHandler).build();
	}

	/**
	 * Image edit headers.
	 *
	 * @param apiKey Stability API key is required for authenticating your requests.
	 * @param contentType The request body’s content type.
	 * @param acceptType accept type of the response.
	 * @param stabilityClientId Specify image/* to get the image bytes directly.
	 * Otherwise, specify application/json to receive the image as base64 encoded JSON.
	 * @param stabilityClientUserId The name of your application, which allows us to
	 * inform you about app-specific debugging or moderation concerns.
	 * @param stabilityClientVersion A unique identifier for the end user, allowing us to
	 * address user-specific debugging or moderation concerns. You can obfuscate this
	 * value to maintain user privacy.
	 * @param additionalHeaders Optional headers you can include in the request. These
	 * headers provide flexibility to pass additional information as needed.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ImageEditHeaders(@JsonProperty("api_key") String apiKey,
			@JsonProperty("content-type") MediaType contentType, @JsonProperty("accept") AcceptType acceptType,
			@JsonProperty("stability-client-id") String stabilityClientId,
			@JsonProperty("stability-client-user-id") String stabilityClientUserId,
			@JsonProperty("stability-client-version") String stabilityClientVersion,
			@JsonProperty("additional_headers") MultiValueMap<String, String> additionalHeaders) {

		public static class Builder {

			private String apiKey;

			private MediaType contentType;

			private AcceptType acceptType;

			private String stabilityClientId;

			private String stabilityClientUserId;

			private String stabilityClientVersion;

			private MultiValueMap<String, String> additionalHeaders;

			public Builder apiKey(String apiKey) {
				this.apiKey = apiKey;
				return this;
			}

			public Builder acceptType(AcceptType acceptType) {
				this.acceptType = acceptType;
				return this;
			}

			public Builder contentType(MediaType contentType) {
				this.contentType = contentType;
				return this;
			}

			public Builder stabilityClientId(String stabilityClientId) {
				this.stabilityClientId = stabilityClientId;
				return this;
			}

			public Builder stabilityClientUserId(String stabilityClientUserId) {
				this.stabilityClientUserId = stabilityClientUserId;
				return this;
			}

			public Builder stabilityClientVersion(String stabilityClientVersion) {
				this.stabilityClientVersion = stabilityClientVersion;
				return this;
			}

			public Builder additionalHeaders(MultiValueMap<String, String> additionalHeaders) {
				this.additionalHeaders = additionalHeaders;
				return this;
			}

			public ImageEditHeaders build() {
				return new ImageEditHeaders(apiKey, contentType, acceptType, stabilityClientId, stabilityClientUserId,
						stabilityClientVersion, additionalHeaders);
			}

		}

		public static Builder builder() {
			return new Builder();
		}

		public HttpHeaders toHttpHeaders() {
			HttpHeaders headers = new HttpHeaders();
			if (apiKey != null) {
				headers.setBearerAuth(apiKey);
			}

			if (acceptType != null) {
				headers.setAccept(List.of(acceptType.getMediaType()));
			}

			if (contentType != null) {
				headers.setContentType(contentType);
			}
			if (stabilityClientId != null) {
				headers.add("stability-client-id", stabilityClientId);
			}
			if (stabilityClientVersion != null) {
				headers.add("stability-client-version", stabilityClientVersion);
			}
			if (stabilityClientUserId != null) {
				headers.add("stability-client-user-id", stabilityClientUserId);
			}
			if (additionalHeaders != null) {
				headers.addAll(additionalHeaders);
			}
			return headers;
		}

		@JsonInclude(Include.NON_NULL)
		public enum AcceptType {

			@JsonProperty("application/json")
			JSON(MediaType.APPLICATION_JSON), @JsonProperty("image/*")
			IMAGE_ALL(new MediaType("image", "*"));

			private final MediaType mediaType;

			AcceptType(MediaType mediaType) {
				this.mediaType = mediaType;
			}

			public MediaType getMediaType() {
				return mediaType;
			}

		}
	}

	/**
	 * This request accurately segments the foreground of an image and removes the
	 * background, enabling a clean separation.
	 *
	 * @param image A binary string representing the image from which you want to remove
	 * the background. Supported Formats: png, webp. Validation Rules: Each side must be
	 * at least 64 pixels, Total pixel count must be between 4,096 and 4,194,304 pixels
	 * @param outputFormat The format of the output image.
	 */
	@JsonInclude(Include.NON_NULL)
	public record RemoveBackgroundRequest(@JsonProperty("image") byte[] image,
			@JsonProperty("output_format") OutputFormat outputFormat) {

		public enum OutputFormat {

			@JsonProperty("png")
			PNG, @JsonProperty("webp")
			WEBP

		}

		public static Builder builder() {
			return new Builder();
		}

		public static class Builder {

			private byte[] image;

			private OutputFormat outputFormat;

			public Builder image(byte[] image) {
				this.image = image;
				return this;
			}

			public Builder outputFormat(OutputFormat outputFormat) {
				this.outputFormat = outputFormat;
				return this;
			}

			public RemoveBackgroundRequest build() {
				return new RemoveBackgroundRequest(image, outputFormat);
			}

		}
	}

	/**
	 * The response from the remove background API.
	 *
	 * @param b64Image The generated image, encoded to base64.
	 * @param finishReason The reason the generation finished. <br>
	 * formats: <br>
	 * * SUCCESS(The generation was completed successfully), <br>
	 * * CONTENT_FILTERED(The generation was completed, but the output violated content
	 * moderation policies and has been blurred.)
	 * @param seed The seed used as random noise for this generation.
	 */
	public record StructuredResponse(@JsonProperty("image") String b64Image,
			@JsonProperty("finish_reason") String finishReason, @JsonProperty("seed") String seed) {

	}

	/**
	 * Remove the background from an image.
	 * @param requestBody The request body.
	 * @param responseType The response type.
	 * @return A ResponseEntity containing the response body and headers from the HTTP
	 * response. Depending on the response type, the body may contain either image data
	 * (byte[]) or structured JSON data.
	 */
	public ResponseEntity<?> removeBackground(RemoveBackgroundRequest requestBody, Class<?> responseType) {

		return removeBackground(requestBody, ImageEditHeaders.builder().build(), responseType);
	}

	/**
	 * Remove the background from an image.
	 * @param requestBody The request body.
	 * @param additionalHeaders Optional, additional headers to include in the request.
	 * @param responseType The response type.
	 * @return A ResponseEntity containing the response body and headers from the HTTP
	 * response. Depending on the response type, the body may contain either image data
	 * (byte[]) or structured JSON data.
	 */
	public <T> ResponseEntity<T> removeBackground(RemoveBackgroundRequest requestBody,
			ImageEditHeaders additionalHeaders, Class<T> responseType) {
		Assert.notNull(requestBody, "Request body must not be null");
		Assert.notNull(requestBody.image(), "Image must not be null");
		Assert.notNull(additionalHeaders, "Additional headers must not be null");

		MultipartBodyBuilder multipartBodyBuilder = createMultipartBodyBuilder(requestBody);

		return restClient.post().uri("/remove-background").body(multipartBodyBuilder.build()).headers(headers -> {
			headers.addAll(additionalHeaders.toHttpHeaders());
		}).retrieve().toEntity(responseType);
	}

	/**
	 * Create a MultipartBodyBuilder for the given request body.
	 * @param requestBody The request body.
	 * @return A MultipartBodyBuilder containing the request body.
	 */
	private MultipartBodyBuilder createMultipartBodyBuilder(RemoveBackgroundRequest requestBody) {
		ByteArrayResource imageResource = new ByteArrayResource(requestBody.image()) {
			@Override
			public String getFilename() {
				return "input_image.png";
			}
		};

		MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
		multipartBodyBuilder.part("image", imageResource);
		if (requestBody.outputFormat() != null) {
			multipartBodyBuilder.part("output_format", requestBody.outputFormat.name().toLowerCase());
		}

		return multipartBodyBuilder;
	}

}
