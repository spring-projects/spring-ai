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
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.stabilityai.api.StabilityAiEditApi.ImageEditHeaders.AcceptType;
import org.springframework.ai.stabilityai.api.StabilityAiEditApi.StructuredResponse.FinishReason;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.MultipartBodyBuilder;
import org.springframework.util.Assert;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClient.RequestHeadersSpec.ConvertibleClientHttpResponse;

/**
 * Edit image using <a href="https://platform.stability.ai/docs/api-reference#tag/Edit">StabilityAI
 * Image Edit (V2 Beta) API</a>
 *
 * @author inpink
 */
public class StabilityAiEditApi {

	private final RestClient restClient;

	/**
	 * Create a new StabilityAI Edit API.
	 *
	 * @param apiKey StabilityAI apiKey.
	 */
	public StabilityAiEditApi(String apiKey) {
		this(apiKey, "https://api.stability.ai/v2beta/stable-image/edit", RestClient.builder(),
				RetryUtils.DEFAULT_RESPONSE_ERROR_HANDLER);
	}

	/**
	 * Create a new StabilityAI Edit API.
	 *
	 * @param apiKey               StabilityAI apiKey.
	 * @param baseUrl              api base URL.
	 * @param restClientBuilder    RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 */
	public StabilityAiEditApi(String apiKey, String baseUrl, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler) {
		this(apiKey, baseUrl, restClientBuilder, responseErrorHandler,
				ImageEditHeaders.builder().build());
	}

	/**
	 * Create a new StabilityAI Edit API.
	 *
	 * @param apiKey               StabilityAI apiKey.
	 * @param baseUrl              api base URL.
	 * @param restClientBuilder    RestClient builder.
	 * @param responseErrorHandler Response error handler.
	 * @param headers              headers.
	 */
	public StabilityAiEditApi(String apiKey, String baseUrl, RestClient.Builder restClientBuilder,
			ResponseErrorHandler responseErrorHandler, ImageEditHeaders headers) {
		this.restClient = restClientBuilder
				.baseUrl(baseUrl)
				.defaultHeaders(h -> {
					h.setBearerAuth(apiKey);
					h.setContentType(MediaType.MULTIPART_FORM_DATA);
					h.setAccept(List.of(AcceptType.IMAGE_ALL.mediaType));
					h.addAll(headers.toHttpHeaders());
				})
				.defaultStatusHandler(responseErrorHandler).build();
	}


	/**
	 * Image edit headers.
	 *
	 * @param apiKey                 Stability API key is required for authenticating your
	 *                               requests.
	 * @param contentType            The request body’s content type.
	 * @param acceptType             accept type of the response.
	 * @param stabilityClientId      Specify image/* to get the image bytes directly. Otherwise,
	 *                               specify application/json to receive the image as base64 encoded
	 *                               JSON.
	 * @param stabilityClientUserId  The name of your application, which allows us to inform you
	 *                               about app-specific debugging or moderation concerns.
	 * @param stabilityClientVersion A unique identifier for the end user, allowing us to address
	 *                               user-specific debugging or moderation concerns. You can
	 *                               obfuscate this value to maintain user privacy.
	 * @param additionalHeaders      Optional headers you can include in the request. These headers
	 *                               provide flexibility to pass additional information as needed.
	 */
	@JsonInclude(Include.NON_NULL)
	public record ImageEditHeaders(
			@JsonProperty("api_key") String apiKey,
			@JsonProperty("content-type") MediaType contentType,
			@JsonProperty("accept") AcceptType acceptType,
			@JsonProperty("stability-client-id") String stabilityClientId,
			@JsonProperty("stability-client-user-id") String stabilityClientUserId,
			@JsonProperty("stability-client-version") String stabilityClientVersion,
			@JsonProperty("additional_headers") MultiValueMap<String, String> additionalHeaders
	) {

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
				return new ImageEditHeaders(apiKey, contentType, acceptType, stabilityClientId,
						stabilityClientUserId, stabilityClientVersion, additionalHeaders);
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
			@JsonProperty("application/json") JSON(MediaType.APPLICATION_JSON),
			@JsonProperty("image/*") IMAGE_ALL(new MediaType("image", "*"));

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
	 * This request accurately segments the foreground of an image and removes the background,
	 * enabling a clean separation.
	 *
	 * @param image        A binary string representing the image from which you want to remove the
	 *                     background. Supported Formats: jpeg, png, webp. Validation Rules: Each
	 *                     side must be at least 64 pixels, Total pixel count must be between 4,096
	 *                     and 4,194,304 pixels
	 * @param outputFormat The format of the output image.
	 */
	@JsonInclude(Include.NON_NULL)
	public record RemoveBackgroundRequest(
			@JsonProperty("image") byte[] image,
			@JsonProperty("output_format") OutputFormat outputFormat) {

		public enum OutputFormat {
			@JsonProperty("png") PNG,
			@JsonProperty("webp") WEBP
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
	 * Response from the StabilityAI Edit API.
	 *
	 * @param rawImage     The bytes of the generated image.
	 * @param b64Image     The generated image, encoded to base64.
	 * @param xRequestId   The unique identifier for the request.
	 * @param contentType  The format of the generated image. To get the raw image bytes, use
	 *                     image/* in the accept header. To receive the image as a base64 string
	 *                     within a JSON response, use application/json.
	 * @param finishReason The reason the generation finished. SUCCESS = The generation was
	 *                     completed successfully. CONTENT_FILTERED = The generation was completed,
	 *                     but the output violated content moderation policies and has been
	 *                     blurred.
	 * @param seed         The seed used as random noise for this generation.
	 */
	@JsonInclude(Include.NON_NULL)
	public record StructuredResponse(
			@JsonProperty("result") byte[] rawImage,
			@JsonProperty("image") String b64Image,
			@JsonProperty("x-request-id") String xRequestId,
			@JsonProperty("content-type") String contentType,
			@JsonProperty("finish_reason") FinishReason finishReason,
			@JsonProperty("seed") String seed) {

		public enum FinishReason {
			SUCCESS,
			CONTENT_FILTERED
		}

		public static Builder builder() {
			return new Builder();
		}

		public static class Builder {

			private byte[] rawImage;
			private String b64Image;
			private String xRequestId;
			private String contentType;
			private FinishReason finishReason;
			private String seed;

			public Builder rawImage(byte[] rawImage) {
				this.rawImage = rawImage;
				return this;
			}

			public Builder b64Image(String b64Image) {
				this.b64Image = b64Image;
				return this;
			}

			public Builder xRequestId(String xRequestId) {
				this.xRequestId = xRequestId;
				return this;
			}

			public Builder contentType(String contentType) {
				this.contentType = contentType;
				return this;
			}

			public Builder finishReason(FinishReason finishReason) {
				this.finishReason = finishReason;
				return this;
			}

			public Builder seed(String seed) {
				this.seed = seed;
				return this;
			}

			public StructuredResponse build() {
				return new StructuredResponse(rawImage, b64Image, xRequestId, contentType,
						finishReason, seed);
			}
		}
	}

	/**
	 * Remove the background from an image.
	 *
	 * @param requestBody The request body.
	 * @return ResponseEntity with StructuredResponse containing the body and headers from the HTTP response. Depending
	 * on the requestBody's acceptType, the required data is either in the headers or the body.
	 */
	public ResponseEntity<StructuredResponse> removeBackground(RemoveBackgroundRequest requestBody) {
		return removeBackground(requestBody, ImageEditHeaders.builder().build());
	}

	/**
	 * Remove the background from an image.
	 *
	 * @param requestBody       The request body.
	 * @param additionalHeaders Optional, additional headers to include in the request.
	 * @return ResponseEntity with StructuredResponse containing the body and headers from the HTTP response. Depending
	 * on the requestBody's acceptType, the required data is either in the headers or the body.
	 */
	public ResponseEntity<StructuredResponse> removeBackground(RemoveBackgroundRequest requestBody,
			ImageEditHeaders additionalHeaders) {
		Assert.notNull(requestBody, "Request body must not be null");
		Assert.notNull(requestBody.image(), "Image must not be null");
		Assert.notNull(additionalHeaders, "Additional headers must not be null");

		ByteArrayResource imageResource = new ByteArrayResource(requestBody.image()) {
			@Override
			public String getFilename() {
				return "input_image.png";
			}
		};
		MultipartBodyBuilder multipartBodyBuilder = new MultipartBodyBuilder();
		multipartBodyBuilder.part("image", imageResource);
		if (requestBody.outputFormat() != null) {
			multipartBodyBuilder.part("output_format",
					requestBody.outputFormat.name().toLowerCase());
		}

		return restClient.post()
				.uri("/remove-background")
				.body(multipartBodyBuilder.build())
				.headers(headers -> {
					headers.addAll(additionalHeaders.toHttpHeaders());
				})
				.exchange((request, response) -> {
					MediaType acceptType = request.getHeaders().getAccept().get(0);
					if (acceptType.equals(AcceptType.IMAGE_ALL.mediaType)) {
						return ResponseEntity.ok(extractRawImageFromResponse(response));
					}

					return ResponseEntity.ok(extractB64ImageFromResponse(response));
				});
	}

	/**
	 * Extract the raw image from the response.
	 *
	 * @param response The response from the HTTP request.
	 * @return StructuredResponse containing the raw image and headers from the HTTP response.
	 */
	private StructuredResponse extractRawImageFromResponse(ConvertibleClientHttpResponse response)
			throws IOException {
		return StructuredResponse.builder()
				.rawImage(response.getBody().readAllBytes())
				.contentType(response.getHeaders().getFirst("content-type"))
				.xRequestId(response.getHeaders().getFirst("x-request-id"))
				.finishReason(FinishReason.valueOf(
						response.getHeaders().getFirst("finish-reason")))
				.seed(response.getHeaders().getFirst("seed"))
				.build();
	}

	/**
	 * Extract the base64 image from the response.
	 *
	 * @param response The response from the HTTP request.
	 * @return StructuredResponse containing the base64 image and headers from the HTTP response.
	 */
	private StructuredResponse extractB64ImageFromResponse(ConvertibleClientHttpResponse response)
			throws IOException {
		ObjectMapper objectMapper = new ObjectMapper();
		StructuredResponse responseBody = objectMapper.readValue(
				response.getBody(), StructuredResponse.class);

		return StructuredResponse.builder()
				.b64Image(responseBody.b64Image())
				.xRequestId(response.getHeaders().getFirst("x-request-id"))
				.contentType(response.getHeaders().getFirst("content-type"))
				.finishReason(responseBody.finishReason())
				.seed(responseBody.seed())
				.build();
	}
}
