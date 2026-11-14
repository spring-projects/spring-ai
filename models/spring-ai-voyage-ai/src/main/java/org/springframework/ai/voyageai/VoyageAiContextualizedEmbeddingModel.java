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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import io.micrometer.observation.ObservationRegistry;
import org.jspecify.annotations.Nullable;

import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.EmbeddingResponseMetadata;
import org.springframework.ai.embedding.EmbeddingResultMetadata;
import org.springframework.ai.embedding.observation.DefaultEmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationContext;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationDocumentation;
import org.springframework.ai.model.Model;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.voyageai.api.VoyageAiApi;
import org.springframework.core.retry.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.MimeTypeUtils;

/**
 * Voyage AI contextualized chunk embedding model.
 *
 * <p>
 * A contextualized model embeds every chunk of a document together with the rest of the
 * document, so a chunk's vector reflects its context. Each instruction of the request is
 * one document or one query, depending on the {@link VoyageAiApi.InputType}:
 * <ul>
 * <li>{@code DOCUMENT}: the instruction is the full text of a document. Voyage AI chunks
 * it (auto-chunking, with chunks of up to {@value #AUTO_CHUNK_SIZE} tokens) and the
 * response holds one embedding per chunk, in document order. The text of each chunk is
 * the {@link EmbeddingResultMetadata#getDocumentData() document data} of its embedding
 * and the index of the document is its {@link EmbeddingResultMetadata#getDocumentId()
 * document id}.
 * <li>{@code QUERY}: the instruction is a query, sent as it is without chunking. The
 * response holds one embedding per query and no chunk text.
 * </ul>
 *
 * <p>
 * This is deliberately not an {@code EmbeddingModel}: one input yields several vectors,
 * so it does not fit the one-vector-per-text contract that vector stores rely on.
 *
 * <p>
 * The model and the input type are never defaulted. A request with more than
 * {@value VoyageAiApi#MAX_INPUTS_PER_REQUEST} instructions is split into several API
 * calls; the token limit of a request is left to the API.
 *
 * @author Zoltan Fodi
 * @since 2.1.1
 */
public class VoyageAiContextualizedEmbeddingModel implements Model<EmbeddingRequest, EmbeddingResponse> {

	/**
	 * The chunk size, in tokens, requested from Voyage AI when it chunks documents.
	 */
	public static final int AUTO_CHUNK_SIZE = VoyageAiApi.MAX_CHUNK_SIZE;

	private static final EmbeddingModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultEmbeddingModelObservationConvention();

	private final VoyageAiApi voyageAiApi;

	private final VoyageAiContextualizedEmbeddingOptions defaultOptions;

	private final RetryTemplate retryTemplate;

	private final ObservationRegistry observationRegistry;

	private EmbeddingModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public VoyageAiContextualizedEmbeddingModel(VoyageAiApi voyageAiApi,
			VoyageAiContextualizedEmbeddingOptions defaultOptions, RetryTemplate retryTemplate,
			ObservationRegistry observationRegistry) {
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
	public EmbeddingResponse call(EmbeddingRequest request) {
		VoyageAiContextualizedEmbeddingOptions options = mergeOptions(request.getOptions());
		String model = VoyageAiApi.ContextualizedEmbeddingModel.fromValue(options.getModel()).getValue();
		VoyageAiApi.InputType inputType = options.getInputType();
		Assert.notNull(inputType, "The input type must be set to DOCUMENT or QUERY, there is no default");
		EmbeddingRequest embeddingRequest = new EmbeddingRequest(request.getInstructions(), options);

		var observationContext = EmbeddingModelObservationContext.builder()
			.embeddingRequest(embeddingRequest)
			.provider(VoyageAiApi.PROVIDER_NAME)
			.build();

		return EmbeddingModelObservationDocumentation.EMBEDDING_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				boolean documents = inputType == VoyageAiApi.InputType.DOCUMENT;
				List<Embedding> embeddings = new ArrayList<>();
				long totalTokens = 0;
				int documentOffset = 0;
				for (List<String> texts : VoyageAiResponseSupport.partition(request.getInstructions(),
						VoyageAiApi.MAX_INPUTS_PER_REQUEST)) {
					// Auto-chunking takes one string per document; queries are not
					// chunked
					List<?> inputs = documents ? texts : texts.stream().map(List::of).toList();
					var apiRequest = new VoyageAiApi.ContextualizedEmbeddingRequest(inputs, model, inputType,
							options.getDimensions(), documents ? Boolean.TRUE : null,
							documents ? AUTO_CHUNK_SIZE : null);
					var apiResponse = RetryUtils.execute(this.retryTemplate,
							() -> this.voyageAiApi.contextualizedEmbeddings(apiRequest));
					totalTokens += VoyageAiResponseSupport.requireTotalTokens(apiResponse.usage(),
							"contextualized embeddings");
					addEmbeddings(embeddings, apiResponse.data(), texts.size(), documentOffset, documents);
					documentOffset += texts.size();
				}
				var usage = new DefaultUsage((int) totalTokens, 0, (int) totalTokens);
				var response = new EmbeddingResponse(embeddings, new EmbeddingResponseMetadata(model, usage));
				observationContext.setResponse(response);
				return response;
			});
	}

	private static void addEmbeddings(List<Embedding> embeddings, List<VoyageAiApi.ContextualizedDocument> data,
			int expectedDocuments, int documentOffset, boolean documents) {
		if (data.size() != expectedDocuments) {
			throw new IllegalStateException(
					"Voyage AI returned " + data.size() + " results for " + expectedDocuments + " inputs");
		}
		List<VoyageAiApi.ContextualizedDocument> sorted = new ArrayList<>(data);
		sorted.sort(Comparator.comparing(VoyageAiApi.ContextualizedDocument::index));
		for (int i = 0; i < sorted.size(); i++) {
			VoyageAiApi.ContextualizedDocument document = sorted.get(i);
			Assert.state(document.index() == i, "Voyage AI returned results whose indexes are not consecutive");
			Assert.state(documents || document.data().size() == 1,
					"Voyage AI returned several embeddings for a single query");
			List<VoyageAiApi.ContextualizedChunk> chunks = new ArrayList<>(document.data());
			chunks.sort(Comparator.comparing(VoyageAiApi.ContextualizedChunk::index));
			String documentId = String.valueOf(documentOffset + i);
			for (VoyageAiApi.ContextualizedChunk chunk : chunks) {
				embeddings
					.add(new Embedding(chunk.embedding(), embeddings.size(), new EmbeddingResultMetadata(documentId,
							EmbeddingResultMetadata.ModalityType.TEXT, MimeTypeUtils.TEXT_PLAIN, chunk.text())));
			}
		}
	}

	private VoyageAiContextualizedEmbeddingOptions mergeOptions(@Nullable EmbeddingOptions requestOptions) {
		if (requestOptions == null) {
			return this.defaultOptions;
		}
		var builder = VoyageAiContextualizedEmbeddingOptions.builder()
			.model(ModelOptionsUtils.mergeOption(requestOptions.getModel(), this.defaultOptions.getModel()))
			.dimensions(
					ModelOptionsUtils.mergeOption(requestOptions.getDimensions(), this.defaultOptions.getDimensions()));
		if (requestOptions instanceof VoyageAiContextualizedEmbeddingOptions voyageOptions) {
			builder.inputType(
					ModelOptionsUtils.mergeOption(voyageOptions.getInputType(), this.defaultOptions.getInputType()));
		}
		else {
			builder.inputType(this.defaultOptions.getInputType());
		}
		return builder.build();
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

		private VoyageAiContextualizedEmbeddingOptions options = VoyageAiContextualizedEmbeddingOptions.builder()
			.build();

		private RetryTemplate retryTemplate = RetryUtils.DEFAULT_RETRY_TEMPLATE;

		private ObservationRegistry observationRegistry = ObservationRegistry.NOOP;

		public Builder voyageAiApi(VoyageAiApi voyageAiApi) {
			this.voyageAiApi = voyageAiApi;
			return this;
		}

		public Builder options(VoyageAiContextualizedEmbeddingOptions options) {
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

		public VoyageAiContextualizedEmbeddingModel build() {
			Assert.state(this.voyageAiApi != null, "VoyageAiApi must not be null");
			return new VoyageAiContextualizedEmbeddingModel(this.voyageAiApi, this.options, this.retryTemplate,
					this.observationRegistry);
		}

	}

}
