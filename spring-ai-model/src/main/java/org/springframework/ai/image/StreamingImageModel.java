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

package org.springframework.ai.image;

import reactor.core.publisher.Flux;

import org.springframework.ai.model.StreamingModel;

/**
 * Reactive API for streaming image generation. Emits progressive image updates during
 * generation, enabling real-time user feedback.
 *
 * <p>
 * The stream emits partial images (if configured) followed by the final complete image.
 * Partial images are best-effort and model-dependent.
 * </p>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>{@code
 * ImageOptions options = OpenAiImageOptions.builder()
 *     .model("gpt-image-1-mini")
 *     .stream(true)
 *     .partialImages(2)
 *     .build();
 *
 * ImagePrompt prompt = new ImagePrompt("A serene mountain landscape", options);
 * Flux<ImageResponse> stream = streamingImageModel.stream(prompt);
 *
 * stream.subscribe(response -> {
 *     Image image = response.getResult().getOutput();
 *     displayImage(image.getB64Json());
 * });
 * }</pre>
 *
 * @author Alexandros Pappas
 * @since 1.1.0
 * @see ImageModel
 * @see ImagePrompt
 * @see ImageResponse
 */
@FunctionalInterface
public interface StreamingImageModel extends StreamingModel<ImagePrompt, ImageResponse> {

	/**
	 * Streams image generation responses for the given prompt.
	 * @param request the image prompt with generation options
	 * @return reactive stream emitting partial images (if configured) and the final image
	 */
	@Override
	Flux<ImageResponse> stream(ImagePrompt request);

}
