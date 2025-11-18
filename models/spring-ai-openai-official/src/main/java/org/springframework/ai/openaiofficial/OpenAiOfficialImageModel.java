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

package org.springframework.ai.openaiofficial;

import com.openai.client.OpenAIClient;
import com.openai.models.images.ImageGenerateParams;
import io.micrometer.observation.ObservationRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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
import org.springframework.ai.openaiofficial.metadata.OpenAiOfficialImageGenerationMetadata;
import org.springframework.ai.openaiofficial.metadata.OpenAiOfficialImageResponseMetadata;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Objects;

import static org.springframework.ai.openaiofficial.setup.OpenAiOfficialSetup.setupSyncClient;

/**
 * Image Model implementation using the OpenAI Java SDK.
 *
 * @author Julien Dubois
 */
public class OpenAiOfficialImageModel implements ImageModel {

    private static final String DEFAULT_MODEL_NAME = OpenAiOfficialImageOptions.DEFAULT_IMAGE_MODEL;

    private static final ImageModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultImageModelObservationConvention();

    private final Logger logger = LoggerFactory.getLogger(OpenAiOfficialImageModel.class);

    private final OpenAIClient openAiClient;

    private final OpenAiOfficialImageOptions options;

    private final ObservationRegistry observationRegistry;

    private ImageModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

    /**
     * Creates a new OpenAiOfficialImageModel with default options.
     */
    public OpenAiOfficialImageModel() {
        this(null, null, null);
    }

    /**
     * Creates a new OpenAiOfficialImageModel with the given options.
     * @param options the image options
     */
    public OpenAiOfficialImageModel(OpenAiOfficialImageOptions options) {
        this(null, options, null);
    }

    /**
     * Creates a new OpenAiOfficialImageModel with the given observation registry.
     * @param observationRegistry the observation registry
     */
    public OpenAiOfficialImageModel(ObservationRegistry observationRegistry) {
        this(null, null, observationRegistry);
    }

    /**
     * Creates a new OpenAiOfficialImageModel with the given options and observation registry.
     * @param options the image options
     * @param observationRegistry the observation registry
     */
    public OpenAiOfficialImageModel(OpenAiOfficialImageOptions options, ObservationRegistry observationRegistry) {
        this(null, options, observationRegistry);
    }

    /**
     * Creates a new OpenAiOfficialImageModel with the given OpenAI client.
     * @param openAIClient the OpenAI client
     */
    public OpenAiOfficialImageModel(OpenAIClient openAIClient) {
        this(openAIClient, null, null);
    }

    /**
     * Creates a new OpenAiOfficialImageModel with the given OpenAI client and options.
     * @param openAIClient the OpenAI client
     * @param options the image options
     */
    public OpenAiOfficialImageModel(OpenAIClient openAIClient, OpenAiOfficialImageOptions options) {
        this(openAIClient, options, null);
    }

    /**
     * Creates a new OpenAiOfficialImageModel with the given OpenAI client and observation registry.
     * @param openAIClient the OpenAI client
     * @param observationRegistry the observation registry
     */
    public OpenAiOfficialImageModel(OpenAIClient openAIClient, ObservationRegistry observationRegistry) {
        this(openAIClient, null, observationRegistry);
    }

    /**
     * Creates a new OpenAiOfficialImageModel with all configuration options.
     * @param openAiClient the OpenAI client
     * @param options the image options
     * @param observationRegistry the observation registry
     */
    public OpenAiOfficialImageModel(OpenAIClient openAiClient, OpenAiOfficialImageOptions options,
            ObservationRegistry observationRegistry) {

        if (options == null) {
            this.options = OpenAiOfficialImageOptions.builder().model(DEFAULT_MODEL_NAME).build();
        }
        else {
            this.options = options;
        }
        this.openAiClient = Objects.requireNonNullElseGet(openAiClient,
                () -> setupSyncClient(this.options.getBaseUrl(), this.options.getApiKey(), this.options.getCredential(),
                        this.options.getAzureDeploymentName(), this.options.getAzureOpenAIServiceVersion(),
                        this.options.getOrganizationId(), this.options.isAzure(), this.options.isGitHubModels(),
                        this.options.getModel(), this.options.getTimeout(), this.options.getMaxRetries(),
                        this.options.getProxy(), this.options.getCustomHeaders()));
        this.observationRegistry = Objects.requireNonNullElse(observationRegistry, ObservationRegistry.NOOP);
    }

    /**
     * Gets the image options for this model.
     * @return the image options
     */
    public OpenAiOfficialImageOptions getOptions() {
        return this.options;
    }

    @Override
    public ImageResponse call(ImagePrompt imagePrompt) {
        OpenAiOfficialImageOptions options = OpenAiOfficialImageOptions.builder()
            .from(this.options)
            .merge(imagePrompt.getOptions())
            .build();

        ImageGenerateParams imageGenerateParams = options.toOpenAiImageGenerateParams(imagePrompt);

        if (logger.isTraceEnabled()) {
            logger.trace("OpenAiOfficialImageOptions call {} with the following options : {} ", options.getModel(),
                    imageGenerateParams);
        }

        var observationContext = ImageModelObservationContext.builder()
            .imagePrompt(imagePrompt)
            .provider(AiProvider.OPENAI_OFFICIAL.value())
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
                            var metadata = new OpenAiOfficialImageGenerationMetadata(nativeImage.revisedPrompt());
                            return new ImageGeneration(image, metadata);
                        }).toList();
                        ImageResponseMetadata openAiImageResponseMetadata = OpenAiOfficialImageResponseMetadata
                            .from(images);
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

}
