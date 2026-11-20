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

package org.springframework.ai.voyageai;

import java.net.URI;
import java.net.URL;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Objects;

import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.content.Media;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.DocumentEmbeddingModel;
import org.springframework.ai.embedding.DocumentEmbeddingRequest;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.embedding.EmbeddingResultMetadata;
import org.springframework.ai.embedding.EmbeddingResultMetadata.ModalityType;
import org.springframework.ai.embedding.observation.DefaultEmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationContext;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationDocumentation;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.voyageai.api.VoyageAiApi;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;

/**
 * Voyage AI multimodal embedding model.
 *
 * <p>
 * Every {@link Document} is one input and yields one embedding. A document holds either
 * text, or a {@link Media} (an image, or an {@code video/mp4} video for
 * {@code voyage-multimodal-3.5}). To embed text and media together as one input, for
 * example a caption and its image, use {@link #embed(List, EmbeddingOptions)} with
 * {@link VoyageAiMultimodalInput}. The embedding reports the modality of the media of its
 * input, or text. The media data may be a URL ({@code String}, {@code URI} or
 * {@code URL}), a base64 data URL ({@code String}) or the bytes of a file
 * ({@code byte[]}, as {@link Media} builds them from a {@code Resource}). Voyage AI
 * requires the media of one request to be given either all as URLs or all as base64, so a
 * request that mixes the two is rejected.
 *
 * <p>
 * This is a {@link DocumentEmbeddingModel}, not an {@code EmbeddingModel}, so that it
 * does not compete with the text embedding model as the embedding model of a vector
 * store. The model is never defaulted. A request with more than
 * {@value VoyageAiApi#MAX_INPUTS_PER_REQUEST} documents is split into several API calls;
 * the token and pixel limits of a request are left to the API.
 *
 * @author Zoltan Fodi
 * @since 2.1.1
 */
public class VoyageAiMultimodalEmbeddingModel implements DocumentEmbeddingModel {

	private static final List<MimeType> SUPPORTED_IMAGE_TYPES = List.of(MimeTypeUtils.IMAGE_PNG,
			MimeTypeUtils.IMAGE_JPEG, MimeType.valueOf("image/webp"), MimeTypeUtils.IMAGE_GIF);

	private static final MimeType VIDEO_MP4 = MimeType.valueOf("video/mp4");

	private static final EmbeddingModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultEmbeddingModelObservationConvention();

	private final VoyageAiApi voyageAiApi;

	private final VoyageAiMultimodalEmbeddingOptions defaultOptions;

	private final RetryTemplate retryTemplate;

	private final ObservationRegistry observationRegistry;

	private EmbeddingModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public VoyageAiMultimodalEmbeddingModel(VoyageAiApi voyageAiApi, VoyageAiMultimodalEmbeddingOptions defaultOptions,
			RetryTemplate retryTemplate, ObservationRegistry observationRegistry) {
		Assert.notNull(voyageAiApi, "voyageAiApi must not be null");
		Assert.notNull(defaultOptions, "defaultOptions must not be null");
		Assert.notNull(retryTemplate, "retryTemplate must not be null");
		Assert.notNull(observationRegistry, "observationRegistry must not be null");
		this.voyageAiApi = voyageAiApi;
		this.defaultOptions = defaultOptions;
		this.retryTemplate = retryTemplate;
		this.observationRegistry = observationRegistry;
	}

	@Override
	public EmbeddingResponse call(DocumentEmbeddingRequest request) {
		return embed(request.getInstructions().stream().map(VoyageAiMultimodalInput::of).toList(),
				request.getOptions());
	}

	/**
	 * Embed inputs that may combine text and media.
	 * @param inputs the inputs, each yielding one embedding
	 * @param requestOptions options that override the defaults of this model
	 * @return the embeddings, in the order of the inputs
	 */
	public EmbeddingResponse embed(List<VoyageAiMultimodalInput> inputs, @Nullable EmbeddingOptions requestOptions) {
		VoyageAiMultimodalEmbeddingOptions options = mergeOptions(requestOptions);
		VoyageAiApi.MultimodalEmbeddingModel model = VoyageAiApi.MultimodalEmbeddingModel.fromValue(options.getModel());
		List<VoyageAiApi.MultimodalInput> apiInputs = inputs.stream()
			.map(VoyageAiMultimodalEmbeddingModel::toApiInput)
			.toList();

		var observationRequest = new EmbeddingRequest(inputs.stream().map(VoyageAiMultimodalInput::id).toList(),
				options);
		var observationContext = EmbeddingModelObservationContext.builder()
			.embeddingRequest(observationRequest)
			.provider(VoyageAiApi.PROVIDER_NAME)
			.build();

		return EmbeddingModelObservationDocumentation.EMBEDDING_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				List<Embedding> embeddings = new ArrayList<>();
				long totalTokens = 0;
				for (List<VoyageAiApi.MultimodalInput> part : VoyageAiResponseSupport.partition(apiInputs,
						VoyageAiApi.MAX_INPUTS_PER_REQUEST)) {
					var apiRequest = new VoyageAiApi.MultimodalEmbeddingRequest(part, model.getValue(),
							options.getInputType(), options.getTruncation(), options.getDimensions());
					var apiResponse = RetryUtils.execute(this.retryTemplate,
							() -> this.voyageAiApi.multimodalEmbeddings(apiRequest));
					totalTokens += VoyageAiResponseSupport.requireTotalTokens(apiResponse.usage(),
							"multimodal embeddings");
					for (float[] vector : VoyageAiResponseSupport.orderedVectors(apiResponse.data(), part.size())) {
						int index = embeddings.size();
						embeddings.add(new Embedding(vector, index, metadataOf(inputs.get(index))));
					}
				}
				var usage = new DefaultUsage((int) totalTokens, 0, (int) totalTokens);
				var response = new EmbeddingResponse(embeddings,
						new EmbeddingResponseMetadata(model.getValue(), usage));
				observationContext.setResponse(response);
				return response;
			});
	}

	private static VoyageAiApi.MultimodalInput toApiInput(VoyageAiMultimodalInput input) {
		return new VoyageAiApi.MultimodalInput(
				input.parts().stream().map(VoyageAiMultimodalEmbeddingModel::toContentPart).toList());
	}

	private static VoyageAiApi.ContentPart toContentPart(VoyageAiMultimodalInput.Part part) {
		Media media = part.media();
		if (media == null) {
			return VoyageAiApi.ContentPart.text(Objects.requireNonNull(part.text()));
		}
		MimeType mimeType = media.getMimeType();
		String source = mediaSource(media);
		boolean dataUrl = source.startsWith("data:");
		if (mimeType.getType().equals("image")) {
			Assert.isTrue(
					SUPPORTED_IMAGE_TYPES.stream().anyMatch(supported -> supported.equalsTypeAndSubtype(mimeType)),
					() -> "Unsupported image type " + mimeType + ", Voyage AI accepts png, jpeg, webp and gif");
			return dataUrl ? VoyageAiApi.ContentPart.imageBase64(source) : VoyageAiApi.ContentPart.imageUrl(source);
		}
		if (mimeType.getType().equals("video")) {
			Assert.isTrue(VIDEO_MP4.equalsTypeAndSubtype(mimeType),
					() -> "Unsupported video type " + mimeType + ", Voyage AI accepts mp4");
			return dataUrl ? VoyageAiApi.ContentPart.videoBase64(source) : VoyageAiApi.ContentPart.videoUrl(source);
		}
		throw new IllegalArgumentException("Unsupported media type " + mimeType + ", expected an image or a video");
	}

	/**
	 * Return the media as the URL or the base64 data URL Voyage AI expects.
	 */
	private static String mediaSource(Media media) {
		Object data = media.getData();
		if (data instanceof byte[] bytes) {
			return "data:" + media.getMimeType().getType() + "/" + media.getMimeType().getSubtype() + ";base64,"
					+ Base64.getEncoder().encodeToString(bytes);
		}
		if (data instanceof URI || data instanceof URL) {
			return data.toString();
		}
		if (data instanceof String text
				&& (text.startsWith("data:") || text.startsWith("http://") || text.startsWith("https://"))) {
			return text;
		}
		throw new IllegalArgumentException(
				"Media data must be the bytes of a file, an http(s) URL or a base64 data URL");
	}

	private static EmbeddingResultMetadata metadataOf(VoyageAiMultimodalInput input) {
		Media media = input.parts()
			.stream()
			.map(VoyageAiMultimodalInput.Part::media)
			.filter(Objects::nonNull)
			.findFirst()
			.orElse(null);
		if (media == null) {
			return new EmbeddingResultMetadata(input.id(), ModalityType.TEXT, MimeTypeUtils.TEXT_PLAIN,
					input.parts().get(0).text());
		}
		return new EmbeddingResultMetadata(input.id(),
				EmbeddingResultMetadata.ModalityUtils.getModalityType(media.getMimeType()), media.getMimeType(),
				media.getData());
	}

	private VoyageAiMultimodalEmbeddingOptions mergeOptions(@Nullable EmbeddingOptions requestOptions) {
		if (requestOptions == null) {
			return this.defaultOptions;
		}
		var builder = VoyageAiMultimodalEmbeddingOptions.builder()
			.model(ModelOptionsUtils.mergeOption(requestOptions.getModel(), this.defaultOptions.getModel()))
			.dimensions(
					ModelOptionsUtils.mergeOption(requestOptions.getDimensions(), this.defaultOptions.getDimensions()));
		if (requestOptions instanceof VoyageAiMultimodalEmbeddingOptions voyageOptions) {
			builder
				.inputType(
						ModelOptionsUtils.mergeOption(voyageOptions.getInputType(), this.defaultOptions.getInputType()))
				.truncation(ModelOptionsUtils.mergeOption(voyageOptions.getTruncation(),
						this.defaultOptions.getTruncation()));
		}
		else {
			builder.inputType(this.defaultOptions.getInputType()).truncation(this.defaultOptions.getTruncation());
		}
		return builder.build();
	}

	@Override
	public int dimensions() {
		// fails with a clear message when the default options name no valid model
		VoyageAiApi.MultimodalEmbeddingModel.fromValue(this.defaultOptions.getModel());
		Integer dimensions = this.defaultOptions.getDimensions();
		return (dimensions != null) ? dimensions : VoyageAiApi.EmbeddingModel.DEFAULT_DIMENSIONS;
	}

	/**
	 * Use the provided convention for reporting observation data.
	 * @param observationConvention the convention
	 */
	public void setObservationConvention(EmbeddingModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

	public static Builder builder() {
		return new Builder();
	}

	public static final class Builder {

		private @Nullable VoyageAiApi voyageAiApi;

		private VoyageAiMultimodalEmbeddingOptions options = VoyageAiMultimodalEmbeddingOptions.builder().build();

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		public Builder voyageAiApi(VoyageAiApi voyageAiApi) {
			this.voyageAiApi = voyageAiApi;
			return this;
		}

		public Builder options(VoyageAiMultimodalEmbeddingOptions options) {
			this.options = options;
			return this;
		}

		public Builder retryTemplate(RetryTemplate retryTemplate) {
			this.retryTemplate = retryTemplate;
			return this;
		}

		public Builder observationRegistry(ObservationRegistry observationRegistry) {
			this.observationRegistry = observationRegistry;
			return this;
		}

		public VoyageAiMultimodalEmbeddingModel build() {
			Assert.state(this.voyageAiApi != null, "VoyageAiApi must not be null");
			return new VoyageAiMultimodalEmbeddingModel(this.voyageAiApi, this.options, this.retryTemplate,
					this.observationRegistry);
		}

	}

}
