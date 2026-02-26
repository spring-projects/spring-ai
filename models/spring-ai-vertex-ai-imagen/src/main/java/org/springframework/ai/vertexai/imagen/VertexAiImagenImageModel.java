/*
 * Copyright 2025-2026 the original author or authors.
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

package org.springframework.ai.vertexai.imagen;

import com.google.cloud.aiplatform.v1.EndpointName;
import com.google.cloud.aiplatform.v1.PredictRequest;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.protobuf.Value;
import io.micrometer.observation.ObservationRegistry;
import org.springframework.ai.image.Image;
import org.springframework.ai.image.ImageGeneration;
import org.springframework.ai.image.ImageGenerationMetadata;
import org.springframework.ai.image.ImageModel;
import org.springframework.ai.image.ImagePrompt;
import org.springframework.ai.image.ImageResponse;
import org.springframework.ai.image.observation.DefaultImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationContext;
import org.springframework.ai.image.observation.ImageModelObservationConvention;
import org.springframework.ai.image.observation.ImageModelObservationDocumentation;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.vertexai.imagen.VertexAiImagenUtils.ImageInstanceBuilder;
import org.springframework.ai.vertexai.imagen.VertexAiImagenUtils.ImageParametersBuilder;
import org.springframework.ai.vertexai.imagen.metadata.VertexAiImagenImageGenerationMetadata;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * <b>VertexAiImagenImageModel</b> is a class that implements the ImageModel interface. It
 * provides a client for calling the Imagen on Vertex AI image generation API.
 *
 * @author Sami Marzouki
 */
public class VertexAiImagenImageModel implements ImageModel {

	private static final ImageModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultImageModelObservationConvention();

	/**
	 * The default options used for the image completion requests.
	 */
	private final VertexAiImagenImageOptions defaultOptions;

	/**
	 * The connection details for Imagen on Vertex AI.
	 */
	private final VertexAiImagenConnectionDetails connectionDetails;

	/**
	 * The retry template used to retry the Imagen on Vertex AI Image API calls.
	 */
	private final RetryTemplate retryTemplate;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	/**
	 * Conventions to use for generating observations.
	 */
	private ImageModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public VertexAiImagenImageModel(VertexAiImagenConnectionDetails connectionDetails,
			VertexAiImagenImageOptions defaultOptions) {
		this(connectionDetails, defaultOptions, RetryUtils.DEFAULT_RETRY_TEMPLATE);
	}

	public VertexAiImagenImageModel(VertexAiImagenConnectionDetails connectionDetails,
			VertexAiImagenImageOptions defaultOptions, RetryTemplate retryTemplate) {
		this(connectionDetails, defaultOptions, retryTemplate, ObservationRegistry.NOOP);
	}

	public VertexAiImagenImageModel(VertexAiImagenConnectionDetails connectionDetails,
			VertexAiImagenImageOptions defaultOptions, RetryTemplate retryTemplate,
			ObservationRegistry observationRegistry) {
		Assert.notNull(defaultOptions, "options must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");
		this.connectionDetails = connectionDetails;
		this.defaultOptions = defaultOptions;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	private static ImageParametersBuilder getImageParametersBuilder(VertexAiImagenImageOptions finalOptions) {
		ImageParametersBuilder parametersBuilder = ImageParametersBuilder.of();

		if (finalOptions.getN() != null) {
			parametersBuilder.sampleCount(finalOptions.getN());
		}
		if (finalOptions.getSeed() != null) {
			parametersBuilder.seed(finalOptions.getSeed());
		}
		if (finalOptions.getNegativePrompt() != null) {
			parametersBuilder.negativePrompt(finalOptions.getNegativePrompt());
		}
		if (finalOptions.getAspectRatio() != null) {
			parametersBuilder.aspectRatio(finalOptions.getAspectRatio());
		}
		if (finalOptions.getAddWatermark() != null) {
			parametersBuilder.addWatermark(finalOptions.getAddWatermark());
		}
		if (finalOptions.getStorageUri() != null) {
			parametersBuilder.storageUri(finalOptions.getStorageUri());
		}
		if (finalOptions.getPersonGeneration() != null) {
			parametersBuilder.personGeneration(finalOptions.getPersonGeneration());
		}
		if (finalOptions.getSafetySetting() != null) {
			parametersBuilder.safetySetting(finalOptions.getSafetySetting());
		}
		if (finalOptions.getLanguage() != null) {
			parametersBuilder.language(finalOptions.getLanguage());
		}
		if (finalOptions.getEnhancePrompt() != null) {
			parametersBuilder.enhancePrompt(finalOptions.getEnhancePrompt());
		}
		if (finalOptions.getSampleImageSize() != null) {
			parametersBuilder.sampleImageSize(finalOptions.getSampleImageSize());
		}
		if (finalOptions.getOutputOptions() != null) {

			ImageParametersBuilder.OutputOptions outputOptions = ImageParametersBuilder.OutputOptions.of();
			if (finalOptions.getOutputOptions().getMimeType() != null) {
				outputOptions.mimeType(finalOptions.getOutputOptions().getMimeType());
			}
			if (finalOptions.getOutputOptions().getCompressionQuality() != null) {
				outputOptions.compressionQuality(finalOptions.getOutputOptions().getCompressionQuality());
			}

			parametersBuilder.outputOptions(outputOptions.build());
		}

		return parametersBuilder;
	}

	@Override
	public ImageResponse call(ImagePrompt imagePrompt) {
		ImagePrompt finalPrompt = mergedPrompt(imagePrompt);
		VertexAiImagenImageOptions finalOptions = (VertexAiImagenImageOptions) finalPrompt.getOptions();

		var observationContext = ImageModelObservationContext.builder()
			.imagePrompt(finalPrompt)
			.provider(AiProvider.VERTEX_AI.value())
			.build();

		return Objects.requireNonNull(
				ImageModelObservationDocumentation.IMAGE_MODEL_OPERATION
					.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
							this.observationRegistry)
					.observe(() -> {

						PredictionServiceClient client = createPredictionServiceClient();
						EndpointName endpointName = this.connectionDetails.getEndpointName(finalOptions.getModel());
						PredictRequest.Builder predictRequestBuilder = getPredictRequestBuilder(finalPrompt,
								endpointName, finalOptions);
						PredictResponse imageResponse = this.retryTemplate
							.execute(context -> getPredictResponse(client, predictRequestBuilder));
						List<ImageGeneration> imageGenerationList = new ArrayList<>();

						for (Value prediction : imageResponse.getPredictionsList()) {
							Value bytesBase64Encoded = prediction.getStructValue()
								.getFieldsOrThrow("bytesBase64Encoded");
							Value mimeType = prediction.getStructValue().getFieldsOrThrow("mimeType");
							ImageGenerationMetadata metadata = new VertexAiImagenImageGenerationMetadata(
									finalPrompt.getInstructions().get(0).getText(), finalOptions.getModel(),
									mimeType.getStringValue());
							Image image = new Image(null, bytesBase64Encoded.getStringValue());
							imageGenerationList.add(new ImageGeneration(image, metadata));
						}
						ImageResponse response = new ImageResponse(imageGenerationList);

						observationContext.setResponse(response);

						return response;

					}));
	}

	private ImagePrompt mergedPrompt(ImagePrompt originalPrompt) {
		VertexAiImagenImageOptions finalOptions = this.defaultOptions;

		if (originalPrompt.getOptions() != null) {
			var defaultOptionsCopy = VertexAiImagenImageOptions.builder().from(this.defaultOptions).build();
			finalOptions = ModelOptionsUtils.merge(originalPrompt.getOptions(), defaultOptionsCopy,
					VertexAiImagenImageOptions.class);
		}

		return new ImagePrompt(originalPrompt.getInstructions(), finalOptions);
	}

	protected PredictRequest.Builder getPredictRequestBuilder(ImagePrompt imagePrompt, EndpointName endpointName,
			VertexAiImagenImageOptions finalOptions) {
		PredictRequest.Builder predictRequestBuilder = PredictRequest.newBuilder().setEndpoint(endpointName.toString());

		ImageParametersBuilder parametersBuilder = getImageParametersBuilder(finalOptions);
		if (finalOptions.getOutputOptions() != null) {
			ImageParametersBuilder.OutputOptions outputOptionsBuilder = ImageParametersBuilder.OutputOptions.of();
			if (finalOptions.getResponseFormat() != null) {
				outputOptionsBuilder.mimeType(finalOptions.getResponseFormat());
			}
			if (finalOptions.getCompressionQuality() != null) {
				outputOptionsBuilder.compressionQuality(finalOptions.getCompressionQuality());
			}
			parametersBuilder.outputOptions(outputOptionsBuilder.build());
		}

		predictRequestBuilder.setParameters(VertexAiImagenUtils.valueOf(parametersBuilder.build()));

		for (int i = 0; i < imagePrompt.getInstructions().size(); i++) {

			ImageInstanceBuilder instanceBuilder = ImageInstanceBuilder
				.of(imagePrompt.getInstructions().get(i).getText());
			predictRequestBuilder.addInstances(VertexAiImagenUtils.valueOf(instanceBuilder.build()));
		}
		return predictRequestBuilder;
	}

	// for testing
	protected PredictionServiceClient createPredictionServiceClient() {
		try {
			return PredictionServiceClient.create(this.connectionDetails.getPredictionServiceSettings());
		}
		catch (IOException e) {
			throw new RuntimeException(e);
		}
	}

	// for testing
	protected PredictResponse getPredictResponse(PredictionServiceClient client,
			PredictRequest.Builder predictRequestBuilder) {
		return client.predict(predictRequestBuilder.build());
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
