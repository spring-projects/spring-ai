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
import java.util.Optional;

import com.google.genai.Client;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.HarmCategory;
import com.google.genai.types.ImageConfig;
import com.google.genai.types.Part;
import com.google.genai.types.SafetySetting;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;

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
 * A class representing an Image Model using the new Google Gen AI SDK.
 *
 * @author Olivier Le Quellec
 * @since 1.1.0
 */
public class GoogleGenAiImageModel implements ImageModel {

	private static final ImageModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultImageModelObservationConvention();

	private final GoogleGenAiImageOptions options;

	private final GoogleGenAiImageConnectionDetails connectionDetails;

	private final RetryTemplate retryTemplate;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	/**
	 * Conventions to use for generating observations.
	 */
	private ImageModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * The GenAI client instance.
	 */
	private final Client genAiClient;

	public GoogleGenAiImageModel(GoogleGenAiImageConnectionDetails connectionDetails,
			GoogleGenAiImageOptions defaultImageOptions) {
		this(connectionDetails, defaultImageOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public GoogleGenAiImageModel(GoogleGenAiImageConnectionDetails connectionDetails,
			GoogleGenAiImageOptions defaultImageOptions, RetryTemplate retryTemplate) {
		this(connectionDetails, defaultImageOptions, retryTemplate, ObservationRegistry.NOOP);
	}

	public GoogleGenAiImageModel(GoogleGenAiImageConnectionDetails connectionDetails,
			GoogleGenAiImageOptions defaultImageOptions, RetryTemplate retryTemplate,
			ObservationRegistry observationRegistry) {
		Assert.notNull(connectionDetails, "GoogleGenAiImageConnectionDetails must not be null");
		Assert.notNull(defaultImageOptions, "GoogleGenAiImageOptions must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");
		this.options = defaultImageOptions;
		this.connectionDetails = connectionDetails;
		this.genAiClient = connectionDetails.getGenAiClient();
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	@Override
	public ImageResponse call(ImagePrompt prompt) {
		ImagePrompt imagePrompt = buildImagePrompt(prompt);

		var observationContext = ImageModelObservationContext.builder()
			.imagePrompt(imagePrompt)
			.provider(AiProvider.GOOGLE_GENAI_AI.value())
			.build();

		return ImageModelObservationDocumentation.IMAGE_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				GoogleGenAiImageOptions options = (GoogleGenAiImageOptions) imagePrompt.getOptions();
				Assert.notNull(options, "Options must not be null");
				String model = options.getModel();
				Assert.notNull(model, "Model must not be null");
				String modelName = this.connectionDetails.getModelEndpointName(model);

				// Build the GenerateContentConfig
				GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder();

				// Request image output from the content generation endpoint.
				configBuilder.responseModalities("TEXT", "IMAGE");

				if (Objects.nonNull(options.getN())) {
					configBuilder.candidateCount(options.getN());
				}
				if (Objects.nonNull(options.getSeed())) {
					configBuilder.seed(options.getSeed());
				}
				if (Objects.nonNull(options.getTemperature())) {
					configBuilder.temperature(options.getTemperature());
				}
				if (Objects.nonNull(options.getTopP())) {
					configBuilder.topP(options.getTopP());
				}
				if (Objects.nonNull(options.getTopK())) {
					configBuilder.topK(options.getTopK());
				}
				if (Objects.nonNull(options.getMaxOutputTokens())) {
					configBuilder.maxOutputTokens(options.getMaxOutputTokens());
				}
				if (Objects.nonNull(options.getLabels()) && !options.getLabels().isEmpty()) {
					configBuilder.labels(options.getLabels());
				}
				if (Objects.nonNull(options.getSafetyFilterLevel()) && options
					.getSafetyFilterLevel() != GoogleGenAiImageOptions.SafetyFilterLevel.SAFETY_FILTER_LEVEL_UNSPECIFIED) {
					configBuilder.safetySettings(buildSafetySettings(options.getSafetyFilterLevel()));
				}

				// Image specific options are carried by the nested ImageConfig.
				ImageConfig.Builder imageConfigBuilder = ImageConfig.builder();
				boolean hasImageConfig = false;
				if (StringUtils.hasText(options.getAspectRatio())) {
					imageConfigBuilder.aspectRatio(options.getAspectRatio());
					hasImageConfig = true;
				}
				if (StringUtils.hasText(options.getImageSize())) {
					imageConfigBuilder.imageSize(options.getImageSize());
					hasImageConfig = true;
				}
				if (Objects.nonNull(options.getPersonGeneration())) {
					imageConfigBuilder.personGeneration(options.getPersonGeneration().name());
					hasImageConfig = true;
				}
				if (StringUtils.hasText(options.getOutputMimeType())) {
					imageConfigBuilder.outputMimeType(options.getOutputMimeType());
					hasImageConfig = true;
				}
				if (Objects.nonNull(options.getOutputCompressionQuality())) {
					imageConfigBuilder.outputCompressionQuality(options.getOutputCompressionQuality());
					hasImageConfig = true;
				}
				if (hasImageConfig) {
					configBuilder.imageConfig(imageConfigBuilder.build());
				}

				GenerateContentConfig config = configBuilder.build();

				// Convert instructions to single prompt for image

				final String promptText = prompt.getInstructions()
					.stream()
					.map(ImageMessage::getText)
					.filter(StringUtils::hasText)
					.reduce((first, second) -> first + "\n" + second)
					.orElseThrow(() -> new IllegalArgumentException(
							"ImagePrompt must contain at least one non-empty message"));

				GenerateContentResponse imagesResponse = RetryUtils.execute(this.retryTemplate,
						() -> this.genAiClient.models.generateContent(modelName, promptText, config));

				// Process the response: each candidate may contain multiple content
				// parts, so add an ImageGeneration for every part that carries image
				// data.
				final List<ImageGeneration> generationList = imagesResponse.candidates()
					.stream()
					.flatMap(List::stream)
					.map(Candidate::content)
					.flatMap(Optional::stream)
					.map(Content::parts)
					.flatMap(Optional::stream)
					.flatMap(List::stream)
					.map(Part::inlineData)
					.flatMap(Optional::stream)
					.map(blob -> {
						String b64Json = blob.data()
							.map(imageBytes -> Base64.getEncoder().encodeToString(imageBytes))
							.orElse(null);

						Image image = new Image(null, b64Json);

						GoogleGenAiImageGenerationMetadata metadata = new GoogleGenAiImageGenerationMetadata(null, null,
								blob.mimeType().orElse(null), image.getUrl());

						return new ImageGeneration(image, metadata);
					})
					.toList();

				ImageResponse response = new ImageResponse(generationList, new ImageResponseMetadata());

				observationContext.setResponse(response);

				return response;
			});
	}

	ImagePrompt buildImagePrompt(ImagePrompt imagePrompt) {
		@Nullable ImageOptions requestOptions = imagePrompt.getOptions();
		GoogleGenAiImageOptions mergedOptions = this.options;

		if (Objects.nonNull(requestOptions)) {
			GoogleGenAiImageOptions.Builder builder = GoogleGenAiImageOptions.builder()
				.from(this.options)
				.model(ModelOptionsUtils.mergeOption(requestOptions.getModel(), this.options.getModel()))
				.n(ModelOptionsUtils.mergeOption(requestOptions.getN(), this.options.getN()))
				.outputMimeType(ModelOptionsUtils.mergeOption(requestOptions.getResponseFormat(),
						this.options.getResponseFormat()));

			if (requestOptions instanceof GoogleGenAiImageOptions googleOptions) {
				builder.from(googleOptions);
			}

			mergedOptions = builder.build();
		}

		// Validate request options
		if (!StringUtils.hasText(mergedOptions.getModel())) {
			throw new IllegalArgumentException("model cannot be null or empty");
		}

		return new ImagePrompt(imagePrompt.getInstructions(), mergedOptions);
	}

	/**
	 * Applies the configured {@link GoogleGenAiImageOptions.SafetyFilterLevel} as a
	 * {@link SafetySetting} threshold across the harm categories supported by the
	 * {@code generateContent} API. The enum names of {@code SafetyFilterLevel} map
	 * directly onto the SDK {@code HarmBlockThreshold} values.
	 * @param safetyFilterLevel the configured safety filter level
	 * @return the list of safety settings to apply to the request
	 */
	private List<SafetySetting> buildSafetySettings(GoogleGenAiImageOptions.SafetyFilterLevel safetyFilterLevel) {
		String threshold = safetyFilterLevel.name();
		List<HarmCategory.Known> categories = List.of(HarmCategory.Known.HARM_CATEGORY_HARASSMENT,
				HarmCategory.Known.HARM_CATEGORY_HATE_SPEECH, HarmCategory.Known.HARM_CATEGORY_SEXUALLY_EXPLICIT,
				HarmCategory.Known.HARM_CATEGORY_DANGEROUS_CONTENT);
		List<SafetySetting> safetySettings = new ArrayList<>();
		for (HarmCategory.Known category : categories) {
			safetySettings.add(SafetySetting.builder().category(category).threshold(threshold).build());
		}
		return safetySettings;
	}

	/**
	 * Use the provided convention for reporting observation data
	 * @param observationConvention The provided convention
	 */
	public void setObservationConvention(@Nullable ImageModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

}
