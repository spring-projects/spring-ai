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

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.api.OpenAiImageApi;
import org.springframework.ai.openai.api.OpenAiImageApi.ImageModel;
import org.springframework.ai.openai.api.OpenAiImageApi.OpenAiImageRequest;
import org.springframework.ai.openai.api.OpenAiImageApi.OpenAiImageStreamEvent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for streaming image generation with {@link OpenAiImageApi}.
 *
 * @author Alexandros Pappas
 * @since 1.1.0
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
public class OpenAiImageApiStreamingIT {

	OpenAiImageApi openAiImageApi = OpenAiImageApi.builder()
		.apiKey(new SimpleApiKey(System.getenv("OPENAI_API_KEY")))
		.build();

	@Test
	void streamImageWithGptImage1Mini() {
		// Create a streaming request with partial images
		// Using JPEG format to support compression < 100
		OpenAiImageRequest request = new OpenAiImageRequest("A simple red circle",
				ImageModel.GPT_IMAGE_1_MINI.getValue(), 1, "medium", null, "1024x1024", null, "test-user", "opaque",
				"auto", 90, "jpeg", 2, true);

		Flux<OpenAiImageStreamEvent> eventStream = this.openAiImageApi.streamImage(request);

		// Collect all events
		List<OpenAiImageStreamEvent> events = new ArrayList<>();
		StepVerifier.create(eventStream)
			.recordWith(() -> events)
			.expectNextCount(3) // Expecting 2 partial images + 1 final image
			.verifyComplete();

		// Verify we received events
		assertThat(events).isNotEmpty();
		assertThat(events.size()).isGreaterThanOrEqualTo(1); // At least the final image

		// Verify the final event is "image_generation.completed"
		OpenAiImageStreamEvent finalEvent = events.get(events.size() - 1);
		assertThat(finalEvent.type()).isEqualTo("image_generation.completed");
		assertThat(finalEvent.b64Json()).isNotEmpty();
		assertThat(finalEvent.usage()).isNotNull();
		assertThat(finalEvent.usage().totalTokens()).isPositive();

		// Verify partial images if present
		if (events.size() > 1) {
			for (int i = 0; i < events.size() - 1; i++) {
				OpenAiImageStreamEvent partialEvent = events.get(i);
				assertThat(partialEvent.type()).isEqualTo("image_generation.partial_image");
				assertThat(partialEvent.b64Json()).isNotEmpty();
				assertThat(partialEvent.partialImageIndex()).isNotNull();
			}
		}
	}

	@Test
	void streamImageWithGptImage1MiniOnePartialImage() {
		// Create a streaming request with 1 partial image
		// Note: Only GPT-Image models support streaming (not DALL-E)
		OpenAiImageRequest request = new OpenAiImageRequest("A blue square", ImageModel.GPT_IMAGE_1_MINI.getValue(), 1,
				"medium", null, "1024x1024", null, null, "opaque", "auto", 85, "jpeg", 1, true);

		Flux<OpenAiImageStreamEvent> eventStream = this.openAiImageApi.streamImage(request);

		StepVerifier.create(eventStream).expectNextMatches(event -> {
			// First event should be partial or final
			boolean isValidType = "image_generation.partial_image".equals(event.type())
					|| "image_generation.completed".equals(event.type());
			return isValidType && event.b64Json() != null && !event.b64Json().isEmpty();
		}).thenConsumeWhile(event -> event.type() != null && event.b64Json() != null).verifyComplete();
	}

	@Test
	void streamImageWithNoPartialImages() {
		// Create a streaming request with 0 partial images (only final image)
		OpenAiImageRequest request = new OpenAiImageRequest("A green triangle", ImageModel.GPT_IMAGE_1_MINI.getValue(),
				1, "low", null, "1024x1024", null, "test-user", "auto", "auto", 80, "jpeg", 0, true);

		Flux<OpenAiImageStreamEvent> eventStream = this.openAiImageApi.streamImage(request);

		// Collect all events
		List<OpenAiImageStreamEvent> events = new ArrayList<>();
		StepVerifier.create(eventStream)
			.recordWith(() -> events)
			.expectNextCount(1) // Only final image
			.verifyComplete();

		// Should only have one event - the completed one
		assertThat(events).hasSize(1);
		assertThat(events.get(0).type()).isEqualTo("image_generation.completed");
		assertThat(events.get(0).b64Json()).isNotEmpty();
		assertThat(events.get(0).usage()).isNotNull();
	}

	@Test
	void streamImageVerifyMetadata() {
		// Test that all metadata fields are populated correctly
		// Using compression=100 for PNG (PNG only supports compression=100)
		OpenAiImageRequest request = new OpenAiImageRequest("A yellow star", ImageModel.GPT_IMAGE_1_MINI.getValue(), 1,
				"medium", null, "1024x1024", null, "test-user", "transparent", "auto", 100, "png", 1, true);

		Flux<OpenAiImageStreamEvent> eventStream = this.openAiImageApi.streamImage(request);

		StepVerifier.create(eventStream.takeLast(1)) // Take only the final event
			.assertNext(event -> {
				assertThat(event.type()).isEqualTo("image_generation.completed");
				assertThat(event.b64Json()).isNotEmpty();
				assertThat(event.createdAt()).isPositive();
				assertThat(event.size()).isEqualTo("1024x1024");
				assertThat(event.quality()).isEqualTo("medium");
				assertThat(event.background()).isEqualTo("transparent");
				assertThat(event.outputFormat()).isEqualTo("png");
				assertThat(event.usage()).isNotNull();
				assertThat(event.usage().totalTokens()).isPositive();
				assertThat(event.usage().inputTokens()).isNotNull();
				assertThat(event.usage().outputTokens()).isNotNull();
			})
			.verifyComplete();
	}

}
