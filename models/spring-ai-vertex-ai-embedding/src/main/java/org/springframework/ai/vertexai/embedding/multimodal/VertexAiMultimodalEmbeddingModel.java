/*
 * Copyright 2024 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.vertexai.embedding.multimodal;

import com.google.cloud.aiplatform.v1.EndpointName;
import com.google.cloud.aiplatform.v1.PredictRequest;
import com.google.cloud.aiplatform.v1.PredictResponse;
import com.google.cloud.aiplatform.v1.PredictionServiceClient;
import com.google.protobuf.InvalidProtocolBufferException;
import com.google.protobuf.Value;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.Media;
import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.DocumentEmbeddingModel;
import org.springframework.ai.embedding.DocumentEmbeddingRequest;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.embedding.EmbeddingResultMetadata;
import org.springframework.ai.embedding.EmbeddingResultMetadata.ModalityType;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.vertexai.embedding.VertexAiEmbeddigConnectionDetails;
import org.springframework.ai.vertexai.embedding.VertexAiEmbeddingUtils;
import org.springframework.ai.vertexai.embedding.VertexAiEmbeddingUtils.ImageBuilder;
import org.springframework.ai.vertexai.embedding.VertexAiEmbeddingUtils.MultimodalInstanceBuilder;
import org.springframework.ai.vertexai.embedding.VertexAiEmbeddingUtils.VideoBuilder;
import org.springframework.util.Assert;
import org.springframework.util.CollectionUtils;
import org.springframework.util.MimeType;
import org.springframework.util.MimeTypeUtils;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Implementation of the Vertex AI Multimodal Embedding Model. Note: This implementation
 * is not yet fully functional and is subject to change.
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class VertexAiMultimodalEmbeddingModel implements DocumentEmbeddingModel {

	private static final Logger logger = LoggerFactory.getLogger(VertexAiMultimodalEmbeddingModel.class);

	public final VertexAiMultimodalEmbeddingOptions defaultOptions;

	private static final MimeType TEXT_MIME_TYPE = MimeTypeUtils.parseMimeType("text/*");

	private static final MimeType IMAGE_MIME_TYPE = MimeTypeUtils.parseMimeType("image/*");

	private static final MimeType VIDEO_MIME_TYPE = MimeTypeUtils.parseMimeType("video/*");

	private static final List<MimeType> SUPPORTED_IMAGE_MIME_SUB_TYPES = List.of(MimeTypeUtils.IMAGE_JPEG,
			MimeTypeUtils.IMAGE_GIF, MimeTypeUtils.IMAGE_PNG, MimeTypeUtils.parseMimeType("image/bmp"));

	private final VertexAiEmbeddigConnectionDetails connectionDetails;

	public VertexAiMultimodalEmbeddingModel(VertexAiEmbeddigConnectionDetails connectionDetails,
			VertexAiMultimodalEmbeddingOptions defaultEmbeddingOptions) {

		Assert.notNull(defaultEmbeddingOptions, "VertexAiMultimodalEmbeddingOptions must not be null");
		this.defaultOptions = defaultEmbeddingOptions;
		this.connectionDetails = connectionDetails;
	}

	@Override
	public EmbeddingResponse call(DocumentEmbeddingRequest request) {

		EmbeddingResponse finalResponse = new EmbeddingResponse(List.of());

		// merge the runtime and default vertex ai options.
		VertexAiMultimodalEmbeddingOptions mergedOptions = this.defaultOptions;

		if (request.getOptions() != null && request.getOptions() != EmbeddingOptions.EMPTY) {
			var defaultOptionsCopy = VertexAiMultimodalEmbeddingOptions.builder().from(this.defaultOptions).build();
			mergedOptions = ModelOptionsUtils.merge(request.getOptions(), defaultOptionsCopy,
					VertexAiMultimodalEmbeddingOptions.class);
		}

		// Create the Vertex AI Prediction Service client.
		try (PredictionServiceClient client = PredictionServiceClient
			.create(this.connectionDetails.getPredictionServiceSettings())) {

			EndpointName endpointName = this.connectionDetails.getEndpointName(mergedOptions.getModel());

			for (Document document : request.getInstructions()) {
				EmbeddingResponse singleDocResponse = this.doSingleDocumentPrediction(client, endpointName, document,
						mergedOptions);
				var mergedEmbeddings = new ArrayList<>(finalResponse.getResults());
				mergedEmbeddings.addAll(singleDocResponse.getResults());
				finalResponse = new EmbeddingResponse(mergedEmbeddings, singleDocResponse.getMetadata());
			}

		}
		catch (Exception e) {
			throw new RuntimeException(e);
		}

		return finalResponse;
	}

	record DocumentMetadata(String documentId, MimeType mimeType, Object data) {
	}

	private EmbeddingResponse doSingleDocumentPrediction(PredictionServiceClient client, EndpointName endpointName,
			Document document, VertexAiMultimodalEmbeddingOptions mergedOptions) throws InvalidProtocolBufferException {

		var instanceBuilder = MultimodalInstanceBuilder.of();

		Map<ModalityType, DocumentMetadata> documentMetadata = new EnumMap<>(ModalityType.class);

		// optional dimensions parameter
		if (mergedOptions.getDimensions() != null) {
			instanceBuilder.withDimension(mergedOptions.getDimensions());
		}

		// optional text parameter
		if (StringUtils.hasText(document.getContent())) {
			instanceBuilder.withText(document.getContent());
			documentMetadata.put(ModalityType.TEXT,
					new DocumentMetadata(document.getId(), MimeTypeUtils.TEXT_PLAIN, document.getContent()));
		}

		if (!CollectionUtils.isEmpty(document.getMedia())) {

			for (Media media : document.getMedia()) {
				if (media.getMimeType().isCompatibleWith(TEXT_MIME_TYPE)) {
					instanceBuilder.withText(media.getData().toString());
					documentMetadata.put(ModalityType.TEXT,
							new DocumentMetadata(document.getId(), MimeTypeUtils.TEXT_PLAIN, media.getData()));
					if (StringUtils.hasText(document.getContent())) {
						logger.warn("Media type String overrides the Document text content!");
					}
				}
				else if (media.getMimeType().isCompatibleWith(IMAGE_MIME_TYPE)) {
					if (SUPPORTED_IMAGE_MIME_SUB_TYPES.contains(media.getMimeType())) {
						instanceBuilder
							.withImage(ImageBuilder.of(media.getMimeType()).withImageData(media.getData()).build());
						documentMetadata.put(ModalityType.IMAGE,
								new DocumentMetadata(document.getId(), media.getMimeType(), media.getData()));
					}
					else {
						logger.warn("Unsupported image mime type: {}", media.getMimeType());
						throw new IllegalArgumentException("Unsupported image mime type: " + media.getMimeType());
					}
				}
				else if (media.getMimeType().isCompatibleWith(VIDEO_MIME_TYPE)) {
					instanceBuilder.withVideo(VideoBuilder.of(media.getMimeType())
						.withVideoData(media.getData())
						.withStartOffsetSec(mergedOptions.getVideoStartOffsetSec())
						.withEndOffsetSec(mergedOptions.getVideoEndOffsetSec())
						.withIntervalSec(mergedOptions.getVideoIntervalSec())
						.build());
					documentMetadata.put(ModalityType.VIDEO,
							new DocumentMetadata(document.getId(), media.getMimeType(), media.getData()));
				}
				else {
					logger.warn("Unsupported media type: {}", media.getMimeType());
					throw new IllegalArgumentException("Unsupported media type: " + media.getMimeType());
				}
			}
		}

		List<Value> instances = List.of(VertexAiEmbeddingUtils.valueOf(instanceBuilder.build()));

		PredictRequest.Builder predictRequestBuilder = PredictRequest.newBuilder()
			.setEndpoint(endpointName.toString())
			.setParameters(VertexAiEmbeddingUtils.jsonToValue(ModelOptionsUtils.toJsonString(Map.of())))
			.addAllInstances(instances);

		PredictResponse embeddingResponse = client.predict(predictRequestBuilder.build());

		int index = 0;
		List<Embedding> embeddingList = new ArrayList<>();
		for (Value prediction : embeddingResponse.getPredictionsList()) {
			if (prediction.getStructValue().containsFields("textEmbedding")) {
				Value textEmbedding = prediction.getStructValue().getFieldsOrThrow("textEmbedding");
				List<Double> textVector = VertexAiEmbeddingUtils.toVector(textEmbedding);

				var docMetadata = documentMetadata.get(ModalityType.TEXT);
				embeddingList.add(new Embedding(textVector, index++, new EmbeddingResultMetadata(docMetadata.documentId,
						ModalityType.TEXT, docMetadata.mimeType, docMetadata.data)));
			}
			if (prediction.getStructValue().containsFields("imageEmbedding")) {
				Value imageEmbedding = prediction.getStructValue().getFieldsOrThrow("imageEmbedding");
				List<Double> imageVector = VertexAiEmbeddingUtils.toVector(imageEmbedding);

				var docMetadata = documentMetadata.get(ModalityType.IMAGE);
				embeddingList
					.add(new Embedding(imageVector, index++, new EmbeddingResultMetadata(docMetadata.documentId,
							ModalityType.IMAGE, docMetadata.mimeType, docMetadata.data)));
			}
			if (prediction.getStructValue().containsFields("videoEmbeddings")) {
				Value videoEmbeddings = prediction.getStructValue().getFieldsOrThrow("videoEmbeddings");
				if (videoEmbeddings.getListValue().getValues(0).getStructValue().containsFields("embedding")) {
					Value embeddings = videoEmbeddings.getListValue()
						.getValues(0)
						.getStructValue()
						.getFieldsOrThrow("embedding");
					List<Double> videoVector = VertexAiEmbeddingUtils.toVector(embeddings);

					var docMetadata = documentMetadata.get(ModalityType.VIDEO);
					embeddingList
						.add(new Embedding(videoVector, index++, new EmbeddingResultMetadata(docMetadata.documentId,
								ModalityType.VIDEO, docMetadata.mimeType, docMetadata.data)));
				}
			}
		}

		String deploymentModelId = embeddingResponse.getDeployedModelId();

		EmbeddingResponseMetadata responseMetadata = generateResponseMetadata(mergedOptions.getModel(), -1);

		responseMetadata.put("deployment-model-id",
				StringUtils.hasText(deploymentModelId) ? deploymentModelId : "unknown");

		return new EmbeddingResponse(embeddingList, generateResponseMetadata(mergedOptions.getModel(), 0));

	}

	private EmbeddingResponseMetadata generateResponseMetadata(String model, Integer tokenCount) {
		EmbeddingResponseMetadata metadata = new EmbeddingResponseMetadata();
		metadata.put("model", model);
		metadata.put("total-tokens", tokenCount);
		return metadata;
	}

	@Override
	public int dimensions() {
		return KNOWN_EMBEDDING_DIMENSIONS.getOrDefault(this.defaultOptions.getModel(), 768);
	}

	private static final Map<String, Integer> KNOWN_EMBEDDING_DIMENSIONS = Stream
		.of(VertexAiMultimodalEmbeddingModelName.values())
		.collect(Collectors.toMap(VertexAiMultimodalEmbeddingModelName::getName,
				VertexAiMultimodalEmbeddingModelName::getDimensions));

}