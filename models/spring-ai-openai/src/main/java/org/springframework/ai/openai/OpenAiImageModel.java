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

package org.springframework.ai.openai;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import com.openai.client.OpenAIClient;
import com.openai.models.images.ImageGenerateParams;
import io.micrometer.observation.ObservationRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.ImageResponseMetadata;
import org.springframework.ai.image.observation.DefaultImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationContext;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationDocumentation;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.openai.http.okhttp.OpenAiHttpClientBuilderCustomizer;
import org.springframework.ai.openai.metadata.OpenAiImageGenerationMetadata;
import org.springframework.ai.openai.metadata.OpenAiImageResponseMetadata;
import org.springframework.ai.openai.setup.OpenAiSetup;
import org.springframework.util.Assert;

/**
 * Image Model implementation using the OpenAI Java SDK.
 *
 * @author Julien Dubois
 * @author Thomas Vitale
 * @author Hyunjoon Choi
 * @author Christian Tzolov
 * @author Mark Pollack
 */
public class OpenAiImageModel implements ImageModel {

	private static final ImageModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultImageModelObservationConvention();

	private final Log logger = LogFactory.getLog(OpenAiImageModel.class);

	private final OpenAIClient openAiClient;

	private final OpenAiImageOptions options;

	private final ObservationRegistry observationRegistry;

	private ImageModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	/**
	 * Creates a new OpenAiImageModel with default options.
	 */
	public OpenAiImageModel() {
		this(null, null, null);
	}

	/**
	 * Creates a new OpenAiImageModel with the given options.
	 * @param options the image options
	 */
	public OpenAiImageModel(@Nullable OpenAiImageOptions options) {
		this(null, options, null);
	}

	/**
	 * Creates a new OpenAiImageModel with the given observation registry.
	 * @param observationRegistry the observation registry
	 */
	public OpenAiImageModel(@Nullable ObservationRegistry observationRegistry) {
		this(null, null, observationRegistry);
	}

	/**
	 * Creates a new OpenAiImageModel with the given options and observation registry.
	 * @param options the image options
	 * @param observationRegistry the observation registry
	 */
	public OpenAiImageModel(@Nullable OpenAiImageOptions options, @Nullable ObservationRegistry observationRegistry) {
		this(null, options, observationRegistry);
	}

	/**
	 * Creates a new OpenAiImageModel with the given OpenAI client.
	 * @param openAIClient the OpenAI client
	 */
	public OpenAiImageModel(@Nullable OpenAIClient openAIClient) {
		this(openAIClient, null, null);
	}

	/**
	 * Creates a new OpenAiImageModel with the given OpenAI client and options.
	 * @param openAIClient the OpenAI client
	 * @param options the image options
	 */
	public OpenAiImageModel(@Nullable OpenAIClient openAIClient, @Nullable OpenAiImageOptions options) {
		this(openAIClient, options, null);
	}

	/**
	 * Creates a new OpenAiImageModel with the given OpenAI client and observation
	 * registry.
	 * @param openAIClient the OpenAI client
	 * @param observationRegistry the observation registry
	 */
	public OpenAiImageModel(@Nullable OpenAIClient openAIClient, @Nullable ObservationRegistry observationRegistry) {
		this(openAIClient, null, observationRegistry);
	}

	/**
	 * Creates a new OpenAiImageModel with all configuration options.
	 * @param openAiClient the OpenAI client
	 * @param options the image options
	 * @param observationRegistry the observation registry
	 */
	public OpenAiImageModel(@Nullable OpenAIClient openAiClient, @Nullable OpenAiImageOptions options,
			@Nullable ObservationRegistry observationRegistry) {
		this(builder().openAiClient(openAiClient).options(options).observationRegistry(observationRegistry));
	}

	public static Builder builder() {
		return new Builder();
	}

	private OpenAiImageModel(Builder builder) {
		this.options = builder.options != null ? builder.options : OpenAiImageOptions.builder().build();
		this.observationRegistry = Objects.requireNonNullElse(builder.observationRegistry, ObservationRegistry.NOOP);
		this.openAiClient = Objects.requireNonNullElseGet(builder.openAiClient,
				() -> OpenAiSetup.setupSyncClient(this.options.getBaseUrl(), this.options.getApiKey(),
						this.options.getCredential(), this.options.getMicrosoftDeploymentName(),
						this.options.getMicrosoftFoundryServiceVersion(), this.options.getOrganizationId(),
						this.options.isMicrosoftFoundry(), this.options.isGitHubModels(), this.options.getModel(),
						this.options.getTimeout(), this.options.getMaxRetries(), this.options.getProxy(),
						this.options.getCustomHeaders(), this.observationRegistry, null,
						builder.httpClientCustomizers));
	}

	/**
	 * Gets the image options for this model.
	 * @return the image options
	 */
	public OpenAiImageOptions getOptions() {
		return this.options;
	}

	@Override
	public ImageResponse call(ImagePrompt imagePrompt) {
		OpenAiImageOptions options = OpenAiImageOptions.builder()
			.from(this.options)
			.merge(imagePrompt.getOptions())
			.build();

		ImageGenerateParams imageGenerateParams = options.toOpenAiImageGenerateParams(imagePrompt);

		if (logger.isTraceEnabled()) {
			logger.trace("OpenAiImageOptions call " + options.getModel() + " with the following options : "
					+ imageGenerateParams);
		}

		var observationContext = ImageModelObservationContext.builder()
			.imagePrompt(imagePrompt)
			.provider(AiProvider.OPENAI.value())
			.build();

		return Objects.requireNonNull(
				ImageModelObservationDocumentation.IMAGE_MODEL_OPERATION
					.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
							this.observationRegistry)
					.observe(() -> {
						var images = this.openAiClient.images().generate(imageGenerateParams);

						if (images.data().isEmpty() && images.data().get().isEmpty()) {
							throw new IllegalArgumentException("Image generation failed: no image returned");
						}

						List<ImageGeneration> imageGenerations = images.data().get().stream().map(nativeImage -> {
							Image image;
							if (nativeImage.url().isPresent()) {
								image = new Image(nativeImage.url().get(), null);
							}
							else if (nativeImage.b64Json().isPresent()) {
								image = new Image(null, nativeImage.b64Json().get());
							}
							else {
								throw new IllegalArgumentException(
										"Image generation failed: image entry missing url and b64_json");
							}
							var metadata = new OpenAiImageGenerationMetadata(nativeImage.revisedPrompt().orElse(null));
							return new ImageGeneration(image, metadata);
						}).toList();
						ImageResponseMetadata openAiImageResponseMetadata = OpenAiImageResponseMetadata.from(images);
						ImageResponse imageResponse = new ImageResponse(imageGenerations, openAiImageResponseMetadata);
						observationContext.setResponse(imageResponse);
						return imageResponse;
					}));
	}

	/**
	 * Use the provided convention for reporting observation data
	 * @param observationConvention The provided convention
	 */
	public void setObservationConvention(ImageModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	public static final class Builder {

		private @Nullable OpenAIClient openAiClient;

		private @Nullable OpenAiImageOptions options;

		private @Nullable ObservationRegistry observationRegistry;

		private List<OpenAiHttpClientBuilderCustomizer> httpClientCustomizers = new ArrayList<>();

		private Builder() {
		}

		public Builder openAiClient(@Nullable OpenAIClient openAiClient) {
			this.openAiClient = openAiClient;
			return this;
		}

		public Builder options(@Nullable OpenAiImageOptions options) {
			this.options = options;
			return this;
		}

		public Builder observationRegistry(@Nullable ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		/**
		 * Registers an {@link OpenAiHttpClientBuilderCustomizer} that mutates the
		 * underlying OkHttp client builder before the OpenAI clients are constructed. Use
		 * this to attach OkHttp interceptors (e.g. OAuth2 bearer-token injection), swap
		 * the dispatcher executor, or tweak any other OkHttp setting. Customizers are
		 * applied in the order they are registered, after Spring AI's own defaults, so
		 * user code wins.
		 */
		public Builder httpClientBuilderCustomizer(OpenAiHttpClientBuilderCustomizer customizer) {
			Assert.notNull(customizer, "customizer cannot be null");
			this.httpClientCustomizers.add(customizer);
			return this;
		}

		/**
		 * Sets the full list of {@link OpenAiHttpClientBuilderCustomizer customizers} to
		 * apply, replacing any customizers registered earlier on this builder. The order
		 * of the list is preserved when invoking the customizers.
		 */
		public Builder httpClientBuilderCustomizers(List<OpenAiHttpClientBuilderCustomizer> customizers) {
			Assert.notNull(customizers, "customizers cannot be null");
			this.httpClientCustomizers = new ArrayList<>(customizers);
			return this;
		}

		public OpenAiImageModel build() {
			return new OpenAiImageModel(this);
		}

	}

}
