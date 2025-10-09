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

package org.springframework.ai.openai.image;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.OpenAiImageModel;
import org.springframework.ai.openai.OpenAiImageOptions;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.ai.openai.api.OpenAiImageApi.ImageModel;
import org.springframework.web.reactive.function.client.WebClient;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for streaming image generation with {@link OpenAiImageModel}.
 *
 * @author Alexandros Pappas
 * @since 1.1.0
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiImageModelStreamingIT {

	OpenAiImageApi openAiImageApi = OpenAiImageApi.builder()
		.apiKey(new SimpleApiKey(System.getenv("OPENAI_API_KEY")))
		.webClientBuilder(WebClient.builder())
		.build();

	OpenAiImageModel openAiImageModel = new OpenAiImageModel(this.openAiImageApi);

	@Test
	void streamImageWithGptImage1MiniAndPartialImages() {
		// Create prompt with streaming options and 2 partial images
		OpenAiImageOptions options = OpenAiImageOptions.builder()
			.model(ImageModel.GPT_IMAGE_1_MINI.getValue())
			.quality("medium")
			.size("1024x1024")
			.background("opaque")
			.moderation("auto")
			.outputCompression(90)
			.outputFormat("jpeg")
			.partialImages(2)
			.stream(true)
			.build();

		ImagePrompt prompt = new ImagePrompt(new ImageMessage("A simple red circle"), options);

		// Stream the image generation
		Flux<ImageResponse> imageStream = this.openAiImageModel.stream(prompt);

		// Collect all responses
		List<ImageResponse> responses = new ArrayList<>();
		StepVerifier.create(imageStream)
			.recordWith(() -> responses)
			.expectNextCount(1) // At least 1 event (final image is guaranteed)
			.thenConsumeWhile(response -> true) // Consume any additional partial images
			.verifyComplete();

		// Verify we received responses
		assertThat(responses).isNotEmpty();
		assertThat(responses.size()).isGreaterThanOrEqualTo(1); // At least final image

		// Verify each response has proper structure
		for (ImageResponse response : responses) {
			assertThat(response.getResults()).hasSize(1);
			assertThat(response.getResult()).isNotNull();
			assertThat(response.getResult().getOutput()).isNotNull();

			Image image = response.getResult().getOutput();
			assertThat(image.getB64Json()).isNotEmpty();
			assertThat(image.getUrl()).isNull(); // GPT-Image models return base64
		}
	}

	@Test
	void streamImageWithGptImage1() {
		// Create prompt with streaming options and 1 partial image
		// Using JPEG for compression < 100 (PNG only supports compression=100)
		OpenAiImageOptions options = OpenAiImageOptions.builder()
			.model(ImageModel.GPT_IMAGE_1.getValue())
			.quality("high")
			.size("1024x1024")
			.background("opaque") // JPEG doesn't support transparency
			.moderation("auto")
			.outputCompression(85)
			.outputFormat("jpeg")
			.partialImages(1)
			.stream(true)
			.build();

		ImagePrompt prompt = new ImagePrompt(new ImageMessage("A blue square"), options);

		Flux<ImageResponse> imageStream = this.openAiImageModel.stream(prompt);

		StepVerifier.create(imageStream).expectNextMatches(response -> {
			// Verify response structure
			boolean hasResults = !response.getResults().isEmpty();
			boolean hasImage = response.getResult() != null && response.getResult().getOutput() != null;
			boolean hasB64Json = response.getResult().getOutput().getB64Json() != null
					&& !response.getResult().getOutput().getB64Json().isEmpty();
			return hasResults && hasImage && hasB64Json;
		})
			.thenConsumeWhile(
					response -> response.getResult() != null && response.getResult().getOutput().getB64Json() != null)
			.verifyComplete();
	}

	@Test
	void streamImageWithNoPartialImages() {
		// Create prompt with streaming but 0 partial images (only final image)
		OpenAiImageOptions options = OpenAiImageOptions.builder()
			.model(ImageModel.GPT_IMAGE_1_MINI.getValue())
			.quality("low")
			.size("1024x1024")
			.background("auto")
			.moderation("auto")
			.outputCompression(80)
			.outputFormat("jpeg")
			.partialImages(0)
			.stream(true)
			.build();

		ImagePrompt prompt = new ImagePrompt(new ImageMessage("A green triangle"), options);

		Flux<ImageResponse> imageStream = this.openAiImageModel.stream(prompt);

		// Collect all responses
		List<ImageResponse> responses = new ArrayList<>();
		StepVerifier.create(imageStream)
			.recordWith(() -> responses)
			.expectNextCount(1) // Only final image
			.verifyComplete();

		// Should only have one response
		assertThat(responses).hasSize(1);

		ImageResponse response = responses.get(0);
		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResult().getOutput().getB64Json()).isNotEmpty();
	}

	@Test
	void streamImageAutoSetsStreamParameter() {
		// Create prompt WITHOUT stream parameter set
		OpenAiImageOptions options = OpenAiImageOptions.builder()
			.model(ImageModel.GPT_IMAGE_1_MINI.getValue())
			.quality("medium")
			.partialImages(1)
			// Note: NOT setting .stream(true)
			.build();

		ImagePrompt prompt = new ImagePrompt(new ImageMessage("A yellow star"), options);

		// Stream should auto-set stream=true
		Flux<ImageResponse> imageStream = this.openAiImageModel.stream(prompt);

		StepVerifier.create(imageStream.take(1)).assertNext(response -> {
			assertThat(response.getResults()).hasSize(1);
			assertThat(response.getResult().getOutput().getB64Json()).isNotEmpty();
		}).verifyComplete();
	}

	@Test
	void streamImageRejectsNonGptImageModels() {
		// Try to stream with DALL-E 3 (should fail)
		OpenAiImageOptions options = OpenAiImageOptions.builder()
			.model(ImageModel.DALL_E_3.getValue())
			.stream(true)
			.build();

		ImagePrompt prompt = new ImagePrompt(new ImageMessage("A painting"), options);

		Flux<ImageResponse> imageStream = this.openAiImageModel.stream(prompt);

		StepVerifier.create(imageStream)
			.expectErrorMatches(throwable -> throwable instanceof IllegalArgumentException
					&& throwable.getMessage().contains("Streaming is only supported for GPT-Image models"))
			.verify();
	}

	@Test
	void streamImageVerifyResponseMetadata() {
		// Test that response metadata is properly populated
		OpenAiImageOptions options = OpenAiImageOptions.builder()
			.model(ImageModel.GPT_IMAGE_1_MINI.getValue())
			.quality("medium")
			.size("1024x1024")
			.background("transparent")
			.outputCompression(100) // PNG only supports compression=100
			.outputFormat("png")
			.partialImages(1)
			.stream(true)
			.build();

		ImagePrompt prompt = new ImagePrompt(new ImageMessage("A purple hexagon"), options);

		Flux<ImageResponse> imageStream = this.openAiImageModel.stream(prompt);

		// Collect all events to ensure we get the final one
		StepVerifier.create(imageStream).expectNextMatches(response -> {
			// Verify response structure
			assertThat(response.getResults()).hasSize(1);
			assertThat(response.getResult()).isNotNull();
			assertThat(response.getResult().getOutput()).isNotNull();

			// Verify image data is present
			Image image = response.getResult().getOutput();
			assertThat(image.getB64Json()).isNotEmpty();

			// Verify metadata is present
			assertThat(response.getMetadata()).isNotNull();
			assertThat(response.getMetadata().getCreated()).isNotNull();

			return true;
		})
			.thenConsumeWhile(response -> true) // Consume any remaining events
			.verifyComplete();
	}

}
