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
 * The StreamingImageModel interface provides a reactive API for invoking image generation
 * models with streaming response. This enables progressive image generation where partial
 * images are emitted as they become available during the generation process.
 *
 * <p>
 * Streaming image generation is particularly useful for providing real-time feedback to
 * users during long-running image generation tasks. Depending on the model and
 * configuration, the stream may emit:
 * </p>
 * <ul>
 * <li>Partial images at various stages of completion</li>
 * <li>The final complete image</li>
 * </ul>
 *
 * <p>
 * Example usage:
 * </p>
 *
 * <pre>{@code
 * ImagePrompt prompt = new ImagePrompt("A serene mountain landscape");
 * Flux<ImageResponse> imageStream = streamingImageModel.stream(prompt);
 * imageStream.subscribe(response -> {
 * 	// Process each partial or final image response
 * 	Image image = response.getResult().getOutput();
 * 	// Display or save the image
 * });
 * }</pre>
 *
 * @author Alexandros Pappas
 * @since 1.1.0
 * @see ImageModel
 * @see StreamingModel
 * @see ImagePrompt
 * @see ImageResponse
 */
@FunctionalInterface
public interface StreamingImageModel extends StreamingModel<ImagePrompt, ImageResponse> {

	/**
	 * Executes streaming image generation for the given prompt.
	 * @param request the image generation request containing the prompt and options
	 * @return a reactive stream of image responses, potentially including partial images
	 * during generation and the final complete image
	 */
	@Override
	Flux<ImageResponse> stream(ImagePrompt request);

}
