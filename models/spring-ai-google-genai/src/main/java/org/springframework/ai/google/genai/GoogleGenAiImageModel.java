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
package org.springframework.ai.google.genai;

import org.springframework.ai.google.genai.api.GoogleGenAiImageApi;
import org.springframework.ai.google.genai.api.dto.GeminiContent;
import org.springframework.ai.google.genai.api.dto.GeminiImageRequest;
import org.springframework.ai.google.genai.api.dto.GeminiImageResponse;
import org.springframework.ai.image.*;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Spring AI {@link ImageModel} implementation for Gemini image models (for example:
 * "gemini-2.5-flash-image", "gemini-3-pro-image-preview").
 *
 * <p>
 * This model:
 * <ul>
 * <li>Holds default {@link GoogleGenAiImageOptions} passed in the constructor.</li>
 * <li>Merges user {@link ImageOptions} with defaults for each
 * {@link #call(ImagePrompt)}.</li>
 * <li>Allows changing the Gemini model per-request via
 * {@link ImageOptions#getModel()}.</li>
 * <li>Maps {@link ImagePrompt} / {@link GoogleGenAiImagePrompt} to Gemini request
 * DTOs.</li>
 * <li>Maps Gemini image response back to Spring AI {@link ImageResponse}.</li>
 * </ul>
 *
 * <p>
 * Transport / HTTP is delegated to
 * {@link org.springframework.ai.google.genai.api.GoogleGenAiImageApi}.
 *
 * @author Danil Temnikov
 */
public class GoogleGenAiImageModel implements ImageModel {

	private final GoogleGenAiImageApi client;

	/**
	 * Default Gemini image options.
	 *
	 * <p>
	 * Invariant: {@code defaultOptions.getModel()} MUST NOT be {@code null} or blank.
	 */
	private final GoogleGenAiImageOptions defaultOptions;

	/**
	 * Create a model instance with explicit default options.
	 *
	 * <p>
	 * Invariant: {@code defaultOptions.getModel()} must be non-null and non-blank.
	 */
	public GoogleGenAiImageModel(GoogleGenAiImageApi client, GoogleGenAiImageOptions defaultOptions) {

		this.client = Objects.requireNonNull(client, "client must not be null");
		this.defaultOptions = Objects.requireNonNull(defaultOptions, "defaultOptions must not be null");

		String defaultModel = this.defaultOptions.getModel();
		if (defaultModel == null || defaultModel.isBlank()) {
			throw new IllegalArgumentException("defaultOptions.model must not be null or blank");
		}
	}

	@Override
	public ImageResponse call(ImagePrompt request) {
		Objects.requireNonNull(request, "request must not be null");

		// 1. Merge per-call options with defaults.
		GoogleGenAiImageOptions effectiveOptions = mergeOptions(request.getOptions());

		// 2. Build Gemini request DTO from prompt + options.
		GeminiImageRequest geminiRequest = buildGeminiRequest(request, effectiveOptions);

		// 3. Resolve model id:
		// - if user specified in effectiveOptions -> use that
		// - else -> fall back to defaultOptions.model (non-null by invariant)
		String modelId = effectiveOptions.getModel();
		if (modelId == null || modelId.isBlank()) {
			modelId = defaultOptions.getModel();
		}

		// 4. Call Gemini via low-level client.
		GeminiImageResponse geminiResponse = this.client.generateContent(modelId, geminiRequest);

		// 5. Map Gemini response into Spring AI ImageResponse.
		List<ImageGeneration> generations = mapToImageGenerations(geminiResponse);

		return new ImageResponse(generations, new ImageResponseMetadata());
	}

	// ---------------------------------------------------------------------
	// Options merge
	// ---------------------------------------------------------------------

	/**
	 * Merge user-provided {@link ImageOptions} with default
	 * {@link GoogleGenAiImageOptions}.
	 *
	 * <p>
	 * Rules:
	 * <ul>
	 * <li>If user options are {@code null} → return {@code defaultOptions} as-is.</li>
	 * <li>If user options are {@link GoogleGenAiImageOptions} → non-null user fields
	 * override default fields.</li>
	 * <li>If user options are some other {@link ImageOptions} implementation → copy only
	 * interface-level fields (model, n) and keep Gemini-specific fields (aspectRatio,
	 * imageSize, responseModalities) from defaults.</li>
	 * </ul>
	 */
	private GoogleGenAiImageOptions mergeOptions(@Nullable ImageOptions userOptions) {
		if (userOptions == null) {
			return defaultOptions;
		}

		GoogleGenAiImageOptions.ImageConfig imageConfig = defaultOptions.getImageConfig();

		if (!(userOptions instanceof GoogleGenAiImageOptions userGemini)) {
			return GoogleGenAiImageOptions.builder()
					.model(firstNonNull(userOptions.getModel(), defaultOptions.getModel()))
					.imageConfig(imageConfig)
					.build();
		}

		GoogleGenAiImageOptions.ImageConfig userImageConfig = userGemini.getImageConfig();
		if (userImageConfig != null) {
			imageConfig = GoogleGenAiImageOptions.ImageConfig.builder()
					.aspectRatio(firstNonNull(userImageConfig.getAspectRatio(), imageConfig.getAspectRatio()))
					.imageSize(firstNonNull(userImageConfig.getImageSize(), imageConfig.getImageSize()))
					.build();
		}

		return GoogleGenAiImageOptions.builder()
				.model(firstNonNull(userGemini.getModel(), defaultOptions.getModel()))
				.imageConfig(imageConfig)
				.build();
	}

	private static <T> T firstNonNull(@Nullable T candidate, @Nullable T fallback) {
		return candidate != null ? candidate : fallback;
	}

	// ---------------------------------------------------------------------
	// Request building
	// ---------------------------------------------------------------------

	/**
	 * Build a Gemini image-generation request from Spring {@link ImagePrompt} and
	 * effective {@link GoogleGenAiImageOptions}.
	 */
	private GeminiImageRequest buildGeminiRequest(ImagePrompt prompt, GoogleGenAiImageOptions options) {

		List<GeminiContent> contents = new ArrayList<>();

		if (prompt instanceof GoogleGenAiImagePrompt googlePrompt) {
			// Multimodal: text + inline images.
			List<GoogleGenAiImagePrompt.InputPart> parts = googlePrompt.getParts();
			if (parts != null && !parts.isEmpty()) {
				List<GeminiContent.GeminiPart> geminiParts = new ArrayList<>();
				for (GoogleGenAiImagePrompt.InputPart part : parts) {
					if (part instanceof GoogleGenAiImagePrompt.TextPart textPart) {
						geminiParts.add(new GeminiContent.GeminiPartText(textPart.getText()));
					} else if (part instanceof GoogleGenAiImagePrompt.InlineImagePart imgPart) {
						GeminiContent.GeminiPartInlineData.InlineData inlineData = new GeminiContent.GeminiPartInlineData.InlineData(
								imgPart.getMimeType(), imgPart.getBase64Data());
						geminiParts.add(new GeminiContent.GeminiPartInlineData(inlineData));
					}
				}
				if (!geminiParts.isEmpty()) {
					contents.add(new GeminiContent(geminiParts));
				}
			}
		} else {
			// Fallback: text-only prompt using ImagePrompt.getInstructions() as
			// List<ImageMessage>.
			List<ImageMessage> instructions = prompt.getInstructions();

			if (instructions == null || instructions.isEmpty()) {
				throw new IllegalArgumentException(
						"Gemini image generation requires exactly one text instruction when using generic ImagePrompt, "
								+ "but no instructions were provided");
			}

			if (instructions.size() != 1) {
				throw new IllegalArgumentException(
						"Gemini image generation adapter expects exactly one ImageMessage in ImagePrompt.getInstructions(); "
								+ "got " + instructions.size()
								+ ". Use GoogleGenAiImagePrompt for multi-part prompts.");
			}

			ImageMessage message = instructions.get(0);
			String text = (message != null ? message.getText() : null);

			if (text == null || text.isBlank()) {
				throw new IllegalArgumentException(
						"Gemini image generation requires non-empty instruction text in the single ImageMessage");
			}

			GeminiContent.GeminiPartText textPart = new GeminiContent.GeminiPartText(text);
			contents.add(new GeminiContent(Collections.singletonList(textPart)));
		}

		// responseModalities:
		// default to ["IMAGE"] for an ImageModel.
		List<String> responseModalitiesWire = List.of("IMAGE");

		// generationConfig.imageConfig from options (aspectRatio + imageSize).
		GeminiImageRequest.GeminiImageConfig imageConfig = null;
		if (options.getImageConfig() != null) {
			imageConfig = new GeminiImageRequest.GeminiImageConfig(options.getImageConfig());
		}

		GeminiImageRequest.GeminiGenerationConfig generationConfig = new GeminiImageRequest.GeminiGenerationConfig(
				responseModalitiesWire, imageConfig);

		return new GeminiImageRequest(contents, generationConfig);
	}

	// ---------------------------------------------------------------------
	// Response mapping
	// ---------------------------------------------------------------------

	/**
	 * Map Gemini image response to Spring AI {@link ImageGeneration} list.
	 *
	 * <p>
	 * Expected image location: {@code candidates[].content.parts[].inlineData.data}.
	 */
	private List<ImageGeneration> mapToImageGenerations(@Nullable GeminiImageResponse response) {
		if (response == null || response.getCandidates() == null || response.getCandidates().isEmpty()) {
			return Collections.emptyList();
		}

		List<ImageGeneration> generations = new ArrayList<>();

		for (GeminiImageResponse.GeminiCandidate candidate : response.getCandidates()) {
			if (candidate == null || candidate.getContent() == null || candidate.getContent().parts == null) {
				continue;
			}

			for (GeminiContent.GeminiPart part : candidate.getContent().parts) {
				if (part instanceof GeminiContent.GeminiPartInlineData inlinePart) {
					GeminiContent.GeminiPartInlineData.InlineData inlineData = inlinePart.inlineData;
					if (inlineData == null || inlineData.data == null || inlineData.data.isBlank()) {
						continue;
					}

					// Spring AI Image: url = null, b64Json = base64 image data.
					Image image = new Image(null, inlineData.data);
					ImageGeneration generation = new ImageGeneration(image, null);

					generations.add(generation);
				}
			}
		}

		return generations;
	}

}
