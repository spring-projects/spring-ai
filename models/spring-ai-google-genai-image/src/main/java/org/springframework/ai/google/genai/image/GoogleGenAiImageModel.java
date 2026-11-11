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

import java.net.URI;
import java.util.ArrayList;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import com.google.genai.Client;
import com.google.genai.types.Candidate;
import com.google.genai.types.Content;
import com.google.genai.types.GenerateContentConfig;
import com.google.genai.types.GenerateContentResponse;
import com.google.genai.types.GenerateContentResponseUsageMetadata;
import com.google.genai.types.HarmCategory;
import com.google.genai.types.ImageConfig;
import com.google.genai.types.Part;
import com.google.genai.types.SafetySetting;
import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.EmptyUsage;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.content.Media;
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
 * @since 2.0.1
 */
public class GoogleGenAiImageModel implements ImageModel {

	private static final ImageModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultImageModelObservationConvention();

	private final GoogleGenAiImageOptions options;

	private final GoogleGenAiImageConnectionDetails connectionDetails;

	private final RetryTemplate retryTemplate;

	private final ObservationRegistry observationRegistry;

	private ImageModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

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
		final ImagePrompt imagePrompt = buildImagePrompt(prompt);

		final ImageModelObservationContext observationContext = ImageModelObservationContext.builder()
			.imagePrompt(imagePrompt)
			.provider(AiProvider.GOOGLE_GENAI_AI.value())
			.build();

		return ImageModelObservationDocumentation.IMAGE_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				final GoogleGenAiImageOptions options = (GoogleGenAiImageOptions) imagePrompt.getOptions();
				Assert.notNull(options, "Options must not be null");

				final String model = options.getModel();
				Assert.notNull(model, "Model must not be null");

				final String modelName = this.connectionDetails.getModelEndpointName(model);

				final GenerateContentConfig config = getGenerateContentConfig(options);

				final List<Content> contents = imagePrompt.getInstructions()
					.stream()
					.map(GoogleGenAiImageModel::messageToParts)
					.filter(Predicate.not(List::isEmpty))
					.map(parts -> Content.builder().role(MessageType.USER.getValue()).parts(parts).build())
					.toList();

				if (contents.isEmpty()) {
					throw new IllegalArgumentException("ImagePrompt must contain at least one non-empty message");
				}

				final GenerateContentResponse imagesResponse = RetryUtils.execute(this.retryTemplate,
						() -> this.genAiClient.models.generateContent(modelName, contents, config));

				final List<Candidate> candidates = Optional.ofNullable(imagesResponse)
					.map(GenerateContentResponse::candidates)
					.flatMap(Function.identity())
					.orElse(List.of());

				final List<ImageGeneration> generationList = candidates.stream()
					.flatMap(GoogleGenAiImageModel::candidateToImageGenerations)
					.toList();

				final List<String> candidateTexts = candidates.stream()
					.flatMap(candidate -> candidate.content().flatMap(Content::parts).orElse(List.of()).stream())
					.filter(part -> part.inlineData().isEmpty())
					.map(Part::text)
					.flatMap(Optional::stream)
					.toList();

				final ImageResponseMetadata responseMetadata = new ImageResponseMetadata();
				if (Objects.nonNull(imagesResponse)) {
					imagesResponse.modelVersion().ifPresent(version -> responseMetadata.put("model", version));
				}

				// Surface any text returned alongside (or instead of) the image(s),
				// e.g. a refusal or safety explanation, so callers aren't left with
				// an empty result and no signal as to why.

				if (!candidateTexts.isEmpty()) {
					responseMetadata.put("text", String.join(System.lineSeparator(), candidateTexts));
				}

				responseMetadata.setUsage(getUsage(imagesResponse));

				final ImageResponse response = new ImageResponse(generationList, responseMetadata);

				observationContext.setResponse(response);

				return response;
			});
	}

	public void setObservationConvention(@Nullable ImageModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	// Private methods

	private static Stream<ImageGeneration> candidateToImageGenerations(Candidate candidate) {
		final List<Part> parts = candidate.content().flatMap(Content::parts).orElse(List.of());

		final String raiFilteredReason = candidate.finishMessage()
			.orElseGet(() -> candidate.finishReason().map(Object::toString).orElse(null));

		return parts.stream().map(Part::inlineData).flatMap(Optional::stream).map(blob -> {
			final String b64Json = blob.data()
				.map(imageBytes -> Base64.getEncoder().encodeToString(imageBytes))
				.orElse(null);

			final Image image = new Image(null, b64Json);

			final GoogleGenAiImageGenerationMetadata metadata = new GoogleGenAiImageGenerationMetadata(null,
					raiFilteredReason, blob.mimeType().orElse(null), null);

			return new ImageGeneration(image, metadata);
		});
	}

	private static Usage getUsage(@Nullable GenerateContentResponse imagesResponse) {
		return Optional.ofNullable(imagesResponse)
			.flatMap(GenerateContentResponse::usageMetadata)
			.map(GoogleGenAiImageModel::toUsage)
			.orElseGet(EmptyUsage::new);
	}

	private static Usage toUsage(GenerateContentResponseUsageMetadata usageMetadata) {
		return new DefaultUsage(usageMetadata.promptTokenCount().orElse(0),
				usageMetadata.candidatesTokenCount().orElse(0), usageMetadata.totalTokenCount().orElse(0));
	}

	private ImagePrompt buildImagePrompt(ImagePrompt imagePrompt) {
		GoogleGenAiImageOptions mergedOptions = this.options;

		final ImageOptions requestOptions = imagePrompt.getOptions();
		if (Objects.nonNull(requestOptions)) {
			final GoogleGenAiImageOptions.Builder builder = GoogleGenAiImageOptions.builder()
				.model(ModelOptionsUtils.mergeOption(requestOptions.getModel(), this.options.getModel()))
				.n(ModelOptionsUtils.mergeOption(requestOptions.getN(), this.options.getN()));

			if (requestOptions instanceof GoogleGenAiImageOptions googleOptions) {
				builder
					.aspectRatio(ModelOptionsUtils.mergeOption(googleOptions.getAspectRatio(),
							this.options.getAspectRatio()))
					.seed(ModelOptionsUtils.mergeOption(googleOptions.getSeed(), this.options.getSeed()))
					.safetyFilterLevel(ModelOptionsUtils.mergeOption(googleOptions.getSafetyFilterLevel(),
							this.options.getSafetyFilterLevel()))
					.personGeneration(ModelOptionsUtils.mergeOption(googleOptions.getPersonGeneration(),
							this.options.getPersonGeneration()))
					.outputMimeType(ModelOptionsUtils.mergeOption(googleOptions.getOutputMimeType(),
							this.options.getOutputMimeType()))
					.outputCompressionQuality(ModelOptionsUtils.mergeOption(googleOptions.getOutputCompressionQuality(),
							this.options.getOutputCompressionQuality()))
					.labels(ModelOptionsUtils.mergeOption(googleOptions.getLabels(), this.options.getLabels()))
					.imageSize(ModelOptionsUtils.mergeOption(googleOptions.getImageSize(), this.options.getImageSize()))
					.temperature(ModelOptionsUtils.mergeOption(googleOptions.getTemperature(),
							this.options.getTemperature()))
					.topP(ModelOptionsUtils.mergeOption(googleOptions.getTopP(), this.options.getTopP()))
					.topK(ModelOptionsUtils.mergeOption(googleOptions.getTopK(), this.options.getTopK()))
					.maxOutputTokens(ModelOptionsUtils.mergeOption(googleOptions.getMaxOutputTokens(),
							this.options.getMaxOutputTokens()));
			}

			mergedOptions = builder.build();
		}

		if (!StringUtils.hasText(mergedOptions.getModel())) {
			mergedOptions = GoogleGenAiImageOptions.builder()
				.from(mergedOptions)
				.model(GoogleGenAiImageOptions.DEFAULT_MODEL_NAME)
				.build();
		}

		return new ImagePrompt(imagePrompt.getInstructions(), mergedOptions);
	}

	private static GenerateContentConfig getGenerateContentConfig(GoogleGenAiImageOptions options) {
		final GenerateContentConfig.Builder configBuilder = GenerateContentConfig.builder();

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

		final ImageConfig.Builder imageConfigBuilder = getImageConfigBuilder(options);
		if (Objects.nonNull(imageConfigBuilder)) {
			configBuilder.imageConfig(imageConfigBuilder.build());
		}

		return configBuilder.build();
	}

	private static ImageConfig.@Nullable Builder getImageConfigBuilder(GoogleGenAiImageOptions options) {
		final ImageConfig.Builder imageConfigBuilder = ImageConfig.builder();

		boolean hasImageConfig = false;
		if (StringUtils.hasText(options.getAspectRatio())) {
			imageConfigBuilder.aspectRatio(options.getAspectRatio());
			hasImageConfig = true;
		}
		if (StringUtils.hasText(options.getImageSize())) {
			imageConfigBuilder.imageSize(options.getImageSize());
			hasImageConfig = true;
		}
		if (Objects.nonNull(options.getPersonGeneration()) && options
			.getPersonGeneration() != GoogleGenAiImageOptions.PersonGeneration.PERSON_GENERATION_UNSPECIFIED) {
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

		if (!hasImageConfig) {
			return null;
		}

		return imageConfigBuilder;
	}

	private static List<Part> messageToParts(ImageMessage message) {
		final List<Part> parts = new ArrayList<>();

		if (StringUtils.hasText(message.getText())) {
			parts.add(Part.fromText(message.getText()));
		}
		if (!message.getMedia().isEmpty()) {
			parts.addAll(mediaToParts(message.getMedia()));
		}

		return parts;
	}

	private static List<Part> mediaToParts(Collection<Media> media) {
		return media.stream().map(mediaData -> {
			final Object data = mediaData.getData();
			final String mimeType = mediaData.getMimeType().toString();

			if (data instanceof byte[]) {
				return Part.fromBytes((byte[]) data, mimeType);
			}
			if (data instanceof URI || data instanceof String) {
				return Part.fromUri(data.toString(), mimeType);
			}

			throw new IllegalArgumentException("Unsupported media data type: " + data.getClass());
		}).toList();
	}

	private static List<SafetySetting> buildSafetySettings(
			GoogleGenAiImageOptions.SafetyFilterLevel safetyFilterLevel) {
		final String threshold = safetyFilterLevel.name();
		final List<HarmCategory.Known> categories = List.of(HarmCategory.Known.HARM_CATEGORY_HARASSMENT,
				HarmCategory.Known.HARM_CATEGORY_HATE_SPEECH, HarmCategory.Known.HARM_CATEGORY_SEXUALLY_EXPLICIT,
				HarmCategory.Known.HARM_CATEGORY_DANGEROUS_CONTENT);

		final List<SafetySetting> safetySettings = new ArrayList<>();
		for (HarmCategory.Known category : categories) {
			safetySettings.add(SafetySetting.builder().category(category).threshold(threshold).build());
		}

		return safetySettings;
	}

}
