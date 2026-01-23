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

package org.springframework.ai.transformers;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.modality.nlp.preprocess.Tokenizer;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import io.micrometer.observation.ObservationRegistry;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.embedding.AbstractEmbeddingModel;
import org.springframework.ai.embedding.Embedding;
import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.embedding.observation.DefaultEmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationContext;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.embedding.observation.EmbeddingModelObservationDocumentation;
import org.springframework.ai.observation.conventions.AiProvider;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * An implementation of the AbstractEmbeddingModel that uses ONNX-based Transformer models
 * for text embeddings.
 *
 * <p>
 * By default, it uses the all-MiniLM-L6-v2 model, but can be configured to use other
 * ONNX-compatible models. The class supports both CPU and GPU inference, caching of model
 * resources, and various tokenization options.
 * </p>
 *
 * <p>
 * For more information on the underlying SBERT framework, see:
 * <a href="https://www.sbert.net/index.html">SBERT Documentation</a>
 * <a href="https://www.sbert.net/docs/pretrained_models.html">SBERT Pre-trained
 * Models</a>
 * </p>
 *
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class TransformersEmbeddingModel extends AbstractEmbeddingModel implements InitializingBean {

	// ONNX tokenizer for the all-MiniLM-L6-v2 generative
	public static final String DEFAULT_ONNX_TOKENIZER_URI = "https://raw.githubusercontent.com/spring-projects/spring-ai/main/models/spring-ai-transformers/src/main/resources/onnx/all-MiniLM-L6-v2/tokenizer.json";

	// ONNX generative for all-MiniLM-L6-v2 pre-trained transformer:
	// https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2
	public static final String DEFAULT_ONNX_MODEL_URI = "https://media.githubusercontent.com/media/spring-projects/spring-ai/refs/heads/main/models/spring-ai-transformers/src/main/resources/onnx/all-MiniLM-L6-v2/model.onnx";

	public static final String DEFAULT_MODEL_OUTPUT_NAME = "last_hidden_state";

	private static final Log logger = LogFactory.getLog(TransformersEmbeddingModel.class);

	private static final EmbeddingModelObservationConvention DEFAULT_OBSERVATION_CONVENTION = new DefaultEmbeddingModelObservationConvention();

	private static final int EMBEDDING_AXIS = 1;

	/**
	 * Specifies what parts of the {@link Document}'s content and metadata will be used
	 * for computing the embeddings. Applicable for the {@link #embed(Document)} method
	 * only. Has no effect on the {@link #embed(String)} or {@link #embed(List)}. Defaults
	 * to {@link MetadataMode#NONE}.
	 */
	private final MetadataMode metadataMode;

	/**
	 * Observation registry used for instrumentation.
	 */
	private final ObservationRegistry observationRegistry;

	public Map<String, String> tokenizerOptions = Map.of();

	private Resource tokenizerResource = toResource(DEFAULT_ONNX_TOKENIZER_URI);

	private Resource modelResource = toResource(DEFAULT_ONNX_MODEL_URI);

	private int gpuDeviceId = -1;

	/**
	 * DJL, Huggingface tokenizer implementation of the {@link Tokenizer} interface that
	 * converts sentences into token.
	 */
	private HuggingFaceTokenizer tokenizer;

	/**
	 * ONNX runtime configurations: https://onnxruntime.ai/docs/get-started/with-java.html
	 */
	private OrtEnvironment environment;

	/**
	 * Runtime session that wraps the ONNX generative and enables inference calls.
	 */
	private OrtSession session;

	/**
	 * Resource cache directory. Used to cache remote resources, such as the ONNX models,
	 * to the local file system.
	 */
	private String resourceCacheDirectory;

	/**
	 * Allow disabling the resource caching.
	 */
	private boolean disableCaching = false;

	/**
	 * Cache service for caching large {@link Resource} contents, such as the
	 * tokenizerResource and modelResource, on the local file system. Can be
	 * enabled/disabled with the {@link #disableCaching} property and uses the
	 * {@link #resourceCacheDirectory} for local storage.
	 */
	private ResourceCacheService cacheService;

	private String modelOutputName = DEFAULT_MODEL_OUTPUT_NAME;

	private Set<String> onnxModelInputs;

	/**
	 * Conventions to use for generating observations.
	 */
	private EmbeddingModelObservationConvention observationConvention = DEFAULT_OBSERVATION_CONVENTION;

	public TransformersEmbeddingModel() {
		this(MetadataMode.NONE);
	}

	public TransformersEmbeddingModel(MetadataMode metadataMode) {
		this(metadataMode, ObservationRegistry.NOOP);
	}

	public TransformersEmbeddingModel(MetadataMode metadataMode, ObservationRegistry observationRegistry) {
		Assert.notNull(metadataMode, "Metadata mode should not be null");
		Assert.notNull(observationRegistry, "Observation registry should not be null");
		this.metadataMode = metadataMode;
		this.observationRegistry = observationRegistry;
	}

	private static Resource toResource(String uri) {
		return new DefaultResourceLoader().getResource(uri);
	}

	public void setTokenizerOptions(Map<String, String> tokenizerOptions) {
		this.tokenizerOptions = tokenizerOptions;
	}

	public void setDisableCaching(boolean disableCaching) {
		this.disableCaching = disableCaching;
	}

	public void setResourceCacheDirectory(String resourceCacheDir) {
		this.resourceCacheDirectory = resourceCacheDir;
	}

	public void setGpuDeviceId(int gpuDeviceId) {
		this.gpuDeviceId = gpuDeviceId;
	}

	public void setTokenizerResource(Resource tokenizerResource) {
		this.tokenizerResource = tokenizerResource;
	}

	public void setModelResource(Resource modelResource) {
		this.modelResource = modelResource;
	}

	public void setTokenizerResource(String tokenizerResourceUri) {
		this.tokenizerResource = toResource(tokenizerResourceUri);
	}

	public void setModelResource(String modelResourceUri) {
		this.modelResource = toResource(modelResourceUri);
	}

	public void setModelOutputName(String modelOutputName) {
		this.modelOutputName = modelOutputName;
	}

	@Override
	public void afterPropertiesSet() throws Exception {

		this.cacheService = StringUtils.hasText(this.resourceCacheDirectory)
				? new ResourceCacheService(this.resourceCacheDirectory) : new ResourceCacheService();

		// Create a pre-trained HuggingFaceTokenizer instance from tokenizerResource
		// InputStream.
		this.tokenizer = HuggingFaceTokenizer.newInstance(getCachedResource(this.tokenizerResource).getInputStream(),
				this.tokenizerOptions);

		// onnxruntime
		this.environment = OrtEnvironment.getEnvironment();

		try (var sessionOptions = new OrtSession.SessionOptions()) {
			if (this.gpuDeviceId >= 0) {
				sessionOptions.addCUDA(this.gpuDeviceId); // Run on a GPU or with another
				// provider
			}
			this.session = this.environment.createSession(getCachedResource(this.modelResource).getContentAsByteArray(),
					sessionOptions);
		}

		this.onnxModelInputs = this.session.getInputNames();
		Set<String> onnxModelOutputs = this.session.getOutputNames();

		logger.info("Model input names: " + this.onnxModelInputs.stream().collect(Collectors.joining(", ")));
		logger.info("Model output names: " + onnxModelOutputs.stream().collect(Collectors.joining(", ")));

		Assert.isTrue(onnxModelOutputs.contains(this.modelOutputName),
				"The generative output names don't contain expected: " + this.modelOutputName
						+ ". Consider one of the available model outputs: "
						+ onnxModelOutputs.stream().collect(Collectors.joining(", ")));
	}

	private Resource getCachedResource(Resource resource) {
		return this.disableCaching ? resource : this.cacheService.getCachedResource(resource);
	}

	@Override
	public float[] embed(String text) {
		return embed(List.of(text)).get(0);
	}

	@Override
	public float[] embed(Document document) {
		return this.embed(document.getFormattedContent(this.metadataMode));
	}

	@Override
	public EmbeddingResponse embedForResponse(List<String> texts) {
		List<Embedding> data = new ArrayList<>();
		List<float[]> embed = this.embed(texts);
		for (int i = 0; i < embed.size(); i++) {
			data.add(new Embedding(embed.get(i), i));
		}
		return new EmbeddingResponse(data);
	}

	@Override
	public List<float[]> embed(List<String> texts) {
		return this.call(new EmbeddingRequest(texts, EmbeddingOptions.builder().build()))
			.getResults()
			.stream()
			.map(e -> e.getOutput())
			.toList();
	}

	@Override
	public EmbeddingResponse call(EmbeddingRequest request) {

		var observationContext = EmbeddingModelObservationContext.builder()
			.embeddingRequest(request)
			.provider(AiProvider.ONNX.value())
			.build();

		return EmbeddingModelObservationDocumentation.EMBEDDING_MODEL_OPERATION
			.observation(this.observationConvention, DEFAULT_OBSERVATION_CONVENTION, () -> observationContext,
					this.observationRegistry)
			.observe(() -> {
				List<float[]> resultEmbeddings = new ArrayList<>();

				try {

					Encoding[] encodings = this.tokenizer.batchEncode(request.getInstructions());

					long[][] input_ids0 = new long[encodings.length][];
					long[][] attention_mask0 = new long[encodings.length][];
					long[][] token_type_ids0 = new long[encodings.length][];

					for (int i = 0; i < encodings.length; i++) {
						input_ids0[i] = encodings[i].getIds();
						attention_mask0[i] = encodings[i].getAttentionMask();
						token_type_ids0[i] = encodings[i].getTypeIds();
					}

					try (OnnxTensor inputIds = OnnxTensor.createTensor(this.environment, input_ids0);
							OnnxTensor attentionMask = OnnxTensor.createTensor(this.environment, attention_mask0);
							OnnxTensor tokenTypeIds = OnnxTensor.createTensor(this.environment, token_type_ids0);) {

						Map<String, OnnxTensor> modelInputs = Map.of("input_ids", inputIds, "attention_mask",
								attentionMask, "token_type_ids", tokenTypeIds);

						modelInputs = removeUnknownModelInputs(modelInputs);

						// The Run result object is AutoCloseable to prevent references
						// from leaking out. Once the Result object is
						// closed, all it’s child OnnxValues are closed too.
						try (OrtSession.Result results = this.session.run(modelInputs)) {

							// OnnxValue lastHiddenState = results.get(0);
							OnnxValue lastHiddenState = results.get(this.modelOutputName).get();

							// 0 - batch_size (1..x)
							// 1 - sequence_length (128)
							// 2 - embedding dimensions (384)
							float[][][] tokenEmbeddings = (float[][][]) lastHiddenState.getValue();

							try (NDManager manager = NDManager.newBaseManager()) {
								NDArray ndTokenEmbeddings = create(tokenEmbeddings, manager);
								NDArray ndAttentionMask = manager.create(attention_mask0);

								NDArray embedding = meanPooling(ndTokenEmbeddings, ndAttentionMask);

								for (int i = 0; i < embedding.size(0); i++) {
									resultEmbeddings.add(embedding.get(i).toFloatArray());
								}
							}
						}
					}
				}
				catch (OrtException ex) {
					throw new RuntimeException(ex);
				}

				var indexCounter = new AtomicInteger(0);

				EmbeddingResponse embeddingResponse = new EmbeddingResponse(
						resultEmbeddings.stream().map(e -> new Embedding(e, indexCounter.incrementAndGet())).toList());
				observationContext.setResponse(embeddingResponse);

				return embeddingResponse;
			});
	}

	private Map<String, OnnxTensor> removeUnknownModelInputs(Map<String, OnnxTensor> modelInputs) {

		return modelInputs.entrySet()
			.stream()
			.filter(a -> this.onnxModelInputs.contains(a.getKey()))
			.collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

	}

	// Build a NDArray from 3D float array.
	private NDArray create(float[][][] data3d, NDManager manager) {

		FloatBuffer buffer = FloatBuffer.allocate(data3d.length * data3d[0].length * data3d[0][0].length);

		for (float[][] data2d : data3d) {
			for (float[] data1d : data2d) {
				buffer.put(data1d);
			}
		}
		buffer.rewind();

		return manager.create(buffer, new Shape(data3d.length, data3d[0].length, data3d[0][0].length));
	}

	private NDArray meanPooling(NDArray tokenEmbeddings, NDArray attentionMask) {

		NDArray attentionMaskExpanded = attentionMask.expandDims(-1)
			.broadcast(tokenEmbeddings.getShape())
			.toType(DataType.FLOAT32, false);

		// Multiply token embeddings with expanded attention mask
		NDArray weightedEmbeddings = tokenEmbeddings.mul(attentionMaskExpanded);

		// Sum along the appropriate axis
		NDArray sumEmbeddings = weightedEmbeddings.sum(new int[] { EMBEDDING_AXIS });

		// Clamp the attention mask sum to avoid division by zero
		NDArray sumMask = attentionMaskExpanded.sum(new int[] { EMBEDDING_AXIS }).clip(1e-9f, Float.MAX_VALUE);

		// Divide sum embeddings by sum mask
		return sumEmbeddings.div(sumMask);
	}

	/**
	 * Use the provided convention for reporting observation data
	 * @param observationConvention The provided convention
	 */
	public void setObservationConvention(EmbeddingModelObservationConvention observationConvention) {
		Assert.notNull(observationConvention, "observationConvention cannot be null");
		this.observationConvention = observationConvention;
	}

}
