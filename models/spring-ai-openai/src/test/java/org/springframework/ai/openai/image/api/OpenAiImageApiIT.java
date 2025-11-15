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

package org.springframework.ai.openai.image.api;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.ai.openai.api.OpenAiImageApi.ImageModel;
import org.springframework.ai.openai.api.OpenAiImageApi.OpenAiImageRequest;
import org.springframework.ai.openai.api.OpenAiImageApi.OpenAiImageResponse;
import org.springframework.http.ResponseEntity;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link OpenAiImageApi}.
 *
 * @author Alexandros Pappas
 * @since 1.1.0
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiImageApiIT {

	OpenAiImageApi openAiImageApi = OpenAiImageApi.builder()
		.apiKey(new SimpleApiKey(System.getenv("OPENAI_API_KEY")))
		.build();

	@ParameterizedTest(name = "{0} : {displayName}")
	@EnumSource(ImageModel.class)
	void createImageWithAllModels(ImageModel model) {
		OpenAiImageRequest request = new OpenAiImageRequest("A simple geometric pattern", model.getValue());

		ResponseEntity<OpenAiImageResponse> response = this.openAiImageApi.createImage(request);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().created()).isPositive();
		assertThat(response.getBody().data()).isNotEmpty();

		// GPT-Image models return b64_json, DALL-E models can return url
		boolean hasUrl = response.getBody().data().get(0).url() != null
				&& !response.getBody().data().get(0).url().isEmpty();
		boolean hasB64Json = response.getBody().data().get(0).b64Json() != null
				&& !response.getBody().data().get(0).b64Json().isEmpty();
		assertThat(hasUrl || hasB64Json).withFailMessage("Response must contain either url or b64_json").isTrue();
	}

	@ParameterizedTest(name = "{0} : {displayName}")
	@EnumSource(names = { "DALL_E_3" })
	void createImageWithRevisedPrompt(ImageModel model) {
		OpenAiImageRequest request = new OpenAiImageRequest("A painting of a sunset over the ocean", model.getValue());

		ResponseEntity<OpenAiImageResponse> response = this.openAiImageApi.createImage(request);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().data()).hasSize(1);
		assertThat(response.getBody().data().get(0).url()).isNotEmpty();
		// DALL-E 3 provides a revised prompt
		assertThat(response.getBody().data().get(0).revisedPrompt()).isNotEmpty();
	}

	@ParameterizedTest(name = "{0} : {displayName}")
	@EnumSource(names = { "DALL_E_2", "DALL_E_3" })
	void createImageWithBase64Response(ImageModel model) {
		// Note: Only DALL-E models support response_format parameter
		OpenAiImageRequest request = new OpenAiImageRequest("A red apple", model.getValue(), null, null, "b64_json",
				null, null, null, null, null, null, null, null, null);

		ResponseEntity<OpenAiImageResponse> response = this.openAiImageApi.createImage(request);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().data()).hasSize(1);
		assertThat(response.getBody().data().get(0).b64Json()).isNotEmpty();
		assertThat(response.getBody().data().get(0).url()).isNull();
	}

	@ParameterizedTest(name = "{0} : {displayName}")
	@EnumSource(names = { "DALL_E_3" })
	void createImageWithCustomSize(ImageModel model) {
		OpenAiImageRequest request = new OpenAiImageRequest("A minimalist logo", model.getValue(), null, null, null,
				"1792x1024", null, null, null, null, null, null, null, null);

		ResponseEntity<OpenAiImageResponse> response = this.openAiImageApi.createImage(request);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().data()).hasSize(1);
		assertThat(response.getBody().data().get(0).url()).isNotEmpty();
	}

	@ParameterizedTest(name = "{0} : {displayName}")
	@EnumSource(names = { "DALL_E_3" })
	void createImageWithHdQuality(ImageModel model) {
		// Note: quality parameter is only supported by DALL-E 3
		OpenAiImageRequest request = new OpenAiImageRequest("A detailed architectural drawing", model.getValue(), null,
				"hd", null, "1024x1024", null, null, null, null, null, null, null, null);

		ResponseEntity<OpenAiImageResponse> response = this.openAiImageApi.createImage(request);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().data()).hasSize(1);
		assertThat(response.getBody().data().get(0).url()).isNotEmpty();
	}

	@ParameterizedTest(name = "{0} : {displayName}")
	@EnumSource(names = { "DALL_E_3" })
	void createImageWithVividStyle(ImageModel model) {
		// Note: style parameter is only supported by DALL-E 3
		OpenAiImageRequest request = new OpenAiImageRequest("A vibrant abstract painting", model.getValue(), null, null,
				null, "1024x1024", "vivid", null, null, null, null, null, null, null);

		ResponseEntity<OpenAiImageResponse> response = this.openAiImageApi.createImage(request);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().data()).hasSize(1);
		assertThat(response.getBody().data().get(0).url()).isNotEmpty();
	}

	@ParameterizedTest(name = "{0} : {displayName}")
	@EnumSource(names = { "DALL_E_3" })
	void createImageWithNaturalStyle(ImageModel model) {
		// Note: style parameter is only supported by DALL-E 3
		OpenAiImageRequest request = new OpenAiImageRequest("A realistic forest scene", model.getValue(), null, null,
				null, "1024x1024", "natural", null, null, null, null, null, null, null);

		ResponseEntity<OpenAiImageResponse> response = this.openAiImageApi.createImage(request);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().data()).hasSize(1);
		assertThat(response.getBody().data().get(0).url()).isNotEmpty();
	}

	@Test
	void createMultipleImagesWithDallE2() {
		// DALL-E 2 supports multiple images (n > 1)
		OpenAiImageRequest request = new OpenAiImageRequest("A simple icon", ImageModel.DALL_E_2.getValue(), 2, null,
				null, "256x256", null, null, null, null, null, null, null, null);

		ResponseEntity<OpenAiImageResponse> response = this.openAiImageApi.createImage(request);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().data()).hasSize(2);
		assertThat(response.getBody().data().get(0).url()).isNotEmpty();
		assertThat(response.getBody().data().get(1).url()).isNotEmpty();
	}

	// Comprehensive model-specific tests with all parameters

	@Test
	void gptImage1WithAllParameters() {
		// Test GPT-Image-1 with all supported parameters (except partial which requires
		// streaming)
		// Using JPEG format to test compression parameter (Compression less than 100 is
		// not supported for PNG output format)
		OpenAiImageRequest request = new OpenAiImageRequest("A red apple floating in space",
				ImageModel.GPT_IMAGE_1.getValue(), 1, "high", null, "1024x1024", null, "test-user", "opaque", "auto",
				85, "jpeg", null, false);

		ResponseEntity<OpenAiImageResponse> response = this.openAiImageApi.createImage(request);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().data()).hasSize(1);
		assertThat(response.getBody().data().get(0).b64Json()).isNotEmpty();
	}

	@Test
	void gptImage1MiniWithAllParameters() {
		// Test GPT-Image-1-Mini with all supported parameters (except partial which
		// requires streaming)
		OpenAiImageRequest request = new OpenAiImageRequest("A sunset over the ocean",
				ImageModel.GPT_IMAGE_1_MINI.getValue(), 1, "medium", null, "1024x1024", null, "test-user", "opaque",
				"low", 70, "jpeg", null, false);

		ResponseEntity<OpenAiImageResponse> response = this.openAiImageApi.createImage(request);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().data()).hasSize(1);
		assertThat(response.getBody().data().get(0).b64Json()).isNotEmpty();
	}

	@Test
	void gptImage1MiniWithAllParametersNonStreaming() {
		// Test GPT-Image-1-Mini with all supported parameters (non-streaming)
		// Note: stream and partial parameters are not used for createImage() method
		OpenAiImageRequest request = new OpenAiImageRequest("A colorful abstract pattern",
				ImageModel.GPT_IMAGE_1_MINI.getValue(), 1, "auto", null, "1024x1024", null, "test-user", "auto", "auto",
				null, "jpeg", null, false);

		ResponseEntity<OpenAiImageResponse> response = this.openAiImageApi.createImage(request);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().data()).hasSize(1);
		assertThat(response.getBody().data().get(0).b64Json()).isNotEmpty();
	}

	@Test
	void dallE3WithAllParameters() {
		// Test DALL-E 3 with all supported parameters
		OpenAiImageRequest request = new OpenAiImageRequest("A hyper-realistic portrait of a wise old wizard",
				ImageModel.DALL_E_3.getValue(), 1, "hd", "url", "1024x1024", "vivid", "test-user", null, null, null,
				null, null, null);

		ResponseEntity<OpenAiImageResponse> response = this.openAiImageApi.createImage(request);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().data()).hasSize(1);
		assertThat(response.getBody().data().get(0).url()).isNotEmpty();
		assertThat(response.getBody().data().get(0).revisedPrompt()).isNotEmpty();
	}

	@Test
	void dallE2WithAllParameters() {
		// Test DALL-E 2 with all supported parameters
		OpenAiImageRequest request = new OpenAiImageRequest("A simple geometric pattern",
				ImageModel.DALL_E_2.getValue(), 2, null, "b64_json", "512x512", null, "test-user", null, null, null,
				null, null, null);

		ResponseEntity<OpenAiImageResponse> response = this.openAiImageApi.createImage(request);

		assertThat(response).isNotNull();
		assertThat(response.getBody()).isNotNull();
		assertThat(response.getBody().data()).hasSize(2);
		assertThat(response.getBody().data().get(0).b64Json()).isNotEmpty();
		assertThat(response.getBody().data().get(1).b64Json()).isNotEmpty();
	}

}
