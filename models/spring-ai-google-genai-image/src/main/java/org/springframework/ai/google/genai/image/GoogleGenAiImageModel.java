/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.google.genai.image;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import com.google.genai.Client;
import com.google.genai.types.GenerateImagesConfig;
import com.google.genai.types.GenerateImagesResponse;
import com.google.genai.types.GeneratedImage;
import com.google.genai.types.ImagePromptLanguage;
import com.google.genai.types.PersonGeneration;
import com.google.genai.types.SafetyFilterLevel;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageMessage;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImageOptions;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.ai.image.observation.DefaultImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationContext;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationDocumentation;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * {@link ImageModel} implementation backed by Google GenAI Imagen (Gemini Developer API
 * or Vertex AI).
 *
 * @author Olivier Le Quellec
 * @since 1.1.0
 */
public class GoogleGenAiImageModel implements ImageModel {

	private static final Logger logger = LoggerFactory.getLogger(GoogleGenAiImageModel.class);

	private static final ImageModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultImageModelObservationConvention();

	private final GoogleGenAiImageOptions defaultOptions;

	private final GoogleGenAiImageConnectionDetails connectionDetails;

	private final Client genAiClient;

	private final RetryTemplate retryTemplate;

	private final ObservationRegistry observationRegistry;

	private ImageModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public GoogleGenAiImageModel(GoogleGenAiImageConnectionDetails connectionDetails,
			GoogleGenAiImageOptions defaultOptions) {
		this(connectionDetails, defaultOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE, ObservationRegistry.NOOP);
	}

	public GoogleGenAiImageModel(GoogleGenAiImageConnectionDetails connectionDetails,
			GoogleGenAiImageOptions defaultOptions, RetryTemplate retryTemplate) {
		this(connectionDetails, defaultOptions, retryTemplate, ObservationRegistry.NOOP);
	}

	public GoogleGenAiImageModel(GoogleGenAiImageConnectionDetails connectionDetails,
			GoogleGenAiImageOptions defaultOptions, RetryTemplate retryTemplate,
			ObservationRegistry observationRegistry) {
		Assert.notNull(connectionDetails, "GoogleGenAiImageConnectionDetails must not be null");
		Assert.notNull(defaultOptions, "GoogleGenAiImageOptions must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");
		this.connectionDetails = connectionDetails;
		this.genAiClient = connectionDetails.getGenAiClient();
		this.defaultOptions = defaultOptions;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	public GoogleGenAiImageOptions getDefaultOptions() {
		return this.defaultOptions;
	}

	@Override
	public ImageResponse call(ImagePrompt imagePrompt) {
		Assert.notNull(imagePrompt, "ImagePrompt must not be null");
		Assert.notEmpty(imagePrompt.getInstructions(), "ImagePrompt instructions must not be empty");

		GoogleGenAiImageOptions requestOptions = mergeOptions(imagePrompt.getOptions(), this.defaultOptions);
		String model = requestOptions.getModel();
		Assert.hasText(model, "Image model name must not be null or empty");

		String promptText = imagePrompt.getInstructions()
			.stream()
			.map(ImageMessage::getText)
			.filter(StringUtils::hasText)
			.reduce((a, b) -> a + "\n" + b)
			.orElseThrow(() -> new IllegalArgumentException("ImagePrompt must contain at least one non-empty message"));

		// Build a normalized ImagePrompt carrying the merged options (required by the
		// observation context, which asserts options are non-null).
		ImagePrompt observedPrompt = new ImagePrompt(imagePrompt.getInstructions(), requestOptions);

		ImageModelObservationContext observationContext = ImageModelObservationContext.builder()
			.imagePrompt(observedPrompt)
			.provider(AiProvider.GOOGLE_GENAI_AI.value())
			.build();

		return Objects.requireNonNull(
				ImageModelObservationDocumentation.IMAGE_MODEL_OPERATION
					.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
							this.observationRegistry)
					.observe(() -> {
						GenerateImagesConfig config = toGenerateImagesConfig(requestOptions);
						String modelName = this.connectionDetails.getModelEndpointName(model);

						if (logger.isTraceEnabled()) {
							logger.trace("Calling Google GenAI Imagen model {} with prompt length {} and config {}",
									modelName, promptText.length(), config);
						}

						GenerateImagesResponse sdkResponse = RetryUtils.execute(this.retryTemplate,
								() -> this.genAiClient.models.generateImages(modelName, promptText, config));

						ImageResponse imageResponse = convertResponse(sdkResponse);
						observationContext.setResponse(imageResponse);
						return imageResponse;
					}));
	}

	/**
	 * Merge runtime and default options. Runtime options override defaults for portable
	 * {@link ImageOptions} fields; provider-specific fields are merged only when the
	 * runtime options are a {@link GoogleGenAiImageOptions}.
	 */
	GoogleGenAiImageOptions mergeOptions(@Nullable ImageOptions runtimeOptions, GoogleGenAiImageOptions defaults) {
		if (runtimeOptions == null) {
			return GoogleGenAiImageOptions.builder().from(defaults).build();
		}

		GoogleGenAiImageOptions.Builder builder = GoogleGenAiImageOptions.builder()
			.model(ModelOptionsUtils.mergeOption(runtimeOptions.getModel(), defaults.getModel()))
			.n(ModelOptionsUtils.mergeOption(runtimeOptions.getN(), defaults.getN()));

		// Map portable response format / mime type onto outputMimeType
		String responseFormat = ModelOptionsUtils.mergeOption(runtimeOptions.getResponseFormat(),
				defaults.getOutputMimeType());
		builder.outputMimeType(responseFormat);

		// Pre-fill provider-specific defaults
		builder.outputGcsUri(defaults.getOutputGcsUri())
			.negativePrompt(defaults.getNegativePrompt())
			.aspectRatio(defaults.getAspectRatio())
			.guidanceScale(defaults.getGuidanceScale())
			.seed(defaults.getSeed())
			.safetyFilterLevel(defaults.getSafetyFilterLevel())
			.personGeneration(defaults.getPersonGeneration())
			.includeSafetyAttributes(defaults.getIncludeSafetyAttributes())
			.includeRaiReason(defaults.getIncludeRaiReason())
			.language(defaults.getLanguage())
			.outputCompressionQuality(defaults.getOutputCompressionQuality())
			.addWatermark(defaults.getAddWatermark())
			.labels(defaults.getLabels())
			.imageSize(defaults.getImageSize())
			.enhancePrompt(defaults.getEnhancePrompt());

		if (runtimeOptions instanceof GoogleGenAiImageOptions googleRuntime) {
			builder
				.outputGcsUri(
						ModelOptionsUtils.mergeOption(googleRuntime.getOutputGcsUri(), defaults.getOutputGcsUri()))
				.negativePrompt(
						ModelOptionsUtils.mergeOption(googleRuntime.getNegativePrompt(), defaults.getNegativePrompt()))
				.aspectRatio(ModelOptionsUtils.mergeOption(googleRuntime.getAspectRatio(), defaults.getAspectRatio()))
				.guidanceScale(
						ModelOptionsUtils.mergeOption(googleRuntime.getGuidanceScale(), defaults.getGuidanceScale()))
				.seed(ModelOptionsUtils.mergeOption(googleRuntime.getSeed(), defaults.getSeed()))
				.safetyFilterLevel(ModelOptionsUtils.mergeOption(googleRuntime.getSafetyFilterLevel(),
						defaults.getSafetyFilterLevel()))
				.personGeneration(ModelOptionsUtils.mergeOption(googleRuntime.getPersonGeneration(),
						defaults.getPersonGeneration()))
				.includeSafetyAttributes(ModelOptionsUtils.mergeOption(googleRuntime.getIncludeSafetyAttributes(),
						defaults.getIncludeSafetyAttributes()))
				.includeRaiReason(ModelOptionsUtils.mergeOption(googleRuntime.getIncludeRaiReason(),
						defaults.getIncludeRaiReason()))
				.language(ModelOptionsUtils.mergeOption(googleRuntime.getLanguage(), defaults.getLanguage()))
				.outputMimeType(ModelOptionsUtils.mergeOption(googleRuntime.getOutputMimeType(), responseFormat))
				.outputCompressionQuality(ModelOptionsUtils.mergeOption(googleRuntime.getOutputCompressionQuality(),
						defaults.getOutputCompressionQuality()))
				.addWatermark(
						ModelOptionsUtils.mergeOption(googleRuntime.getAddWatermark(), defaults.getAddWatermark()))
				.labels(ModelOptionsUtils.mergeOption(googleRuntime.getLabels(), defaults.getLabels()))
				.imageSize(ModelOptionsUtils.mergeOption(googleRuntime.getImageSize(), defaults.getImageSize()))
				.enhancePrompt(
						ModelOptionsUtils.mergeOption(googleRuntime.getEnhancePrompt(), defaults.getEnhancePrompt()));
		}

		GoogleGenAiImageOptions merged = builder.build();
		if (!StringUtils.hasText(merged.getAspectRatio())) {
			merged.setAspectRatio(GoogleGenAiImageOptions.DEFAULT_ASPECT_RATIO);
		}
		return merged;
	}

	private GenerateImagesConfig toGenerateImagesConfig(GoogleGenAiImageOptions options) {
		GenerateImagesConfig.Builder builder = GenerateImagesConfig.builder();

		if (options.getN() != null) {
			builder.numberOfImages(options.getN());
		}
		if (StringUtils.hasText(options.getOutputGcsUri())) {
			builder.outputGcsUri(options.getOutputGcsUri());
		}
		if (StringUtils.hasText(options.getNegativePrompt())) {
			builder.negativePrompt(options.getNegativePrompt());
		}
		if (StringUtils.hasText(options.getAspectRatio())) {
			builder.aspectRatio(options.getAspectRatio());
		}
		if (options.getGuidanceScale() != null) {
			builder.guidanceScale(options.getGuidanceScale());
		}
		if (options.getSeed() != null) {
			builder.seed(options.getSeed());
		}
		if (options.getSafetyFilterLevel() != null) {
			builder.safetyFilterLevel(new SafetyFilterLevel(options.getSafetyFilterLevel().name()));
		}
		if (options.getPersonGeneration() != null) {
			builder.personGeneration(new PersonGeneration(options.getPersonGeneration().name()));
		}
		if (options.getIncludeSafetyAttributes() != null) {
			builder.includeSafetyAttributes(options.getIncludeSafetyAttributes());
		}
		if (options.getIncludeRaiReason() != null) {
			builder.includeRaiReason(options.getIncludeRaiReason());
		}
		if (StringUtils.hasText(options.getLanguage())) {
			builder.language(new ImagePromptLanguage(options.getLanguage()));
		}
		if (StringUtils.hasText(options.getOutputMimeType())) {
			builder.outputMimeType(options.getOutputMimeType());
		}
		if (options.getOutputCompressionQuality() != null) {
			builder.outputCompressionQuality(options.getOutputCompressionQuality());
		}
		if (options.getAddWatermark() != null) {
			builder.addWatermark(options.getAddWatermark());
		}
		if (options.getLabels() != null && !options.getLabels().isEmpty()) {
			builder.labels(options.getLabels());
		}
		if (StringUtils.hasText(options.getImageSize())) {
			builder.imageSize(options.getImageSize());
		}
		if (options.getEnhancePrompt() != null) {
			builder.enhancePrompt(options.getEnhancePrompt());
		}
		return builder.build();
	}

	private ImageResponse convertResponse(GenerateImagesResponse sdkResponse) {
		List<ImageGeneration> generations = new ArrayList<>();
		if (sdkResponse.generatedImages().isPresent()) {
			for (GeneratedImage generated : sdkResponse.generatedImages().get()) {
				generations.add(toImageGeneration(generated));
			}
		}
		return new ImageResponse(generations, new ImageResponseMetadata());
	}

	private ImageGeneration toImageGeneration(GeneratedImage generated) {
		String b64 = null;
		String url = null;
		String mimeType = null;
		if (generated.image().isPresent()) {
			com.google.genai.types.Image sdkImage = generated.image().get();
			if (sdkImage.imageBytes().isPresent()) {
				b64 = Base64.getEncoder().encodeToString(sdkImage.imageBytes().get());
			}
			if (sdkImage.gcsUri().isPresent()) {
				url = sdkImage.gcsUri().get();
			}
			if (sdkImage.mimeType().isPresent()) {
				mimeType = sdkImage.mimeType().get();
			}
		}
		Image image = new Image(url, b64);
		GoogleGenAiImageGenerationMetadata metadata = new GoogleGenAiImageGenerationMetadata(
				generated.enhancedPrompt().orElse(null), generated.raiFilteredReason().orElse(null), mimeType, url);
		return new ImageGeneration(image, metadata);
	}

	/**
	 * Use the provided convention for reporting observation data.
	 * @param observationConvention The provided convention
	 */
	public void setObservationConvention(ImageModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

}
