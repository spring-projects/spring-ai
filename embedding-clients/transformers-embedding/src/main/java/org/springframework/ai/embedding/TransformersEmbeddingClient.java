package org.springframework.ai.embedding;

import java.nio.FloatBuffer;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

import ai.djl.huggingface.tokenizers.Encoding;
import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.djl.ndarray.NDArray;
import ai.djl.ndarray.NDManager;
import ai.djl.ndarray.types.DataType;
import ai.djl.ndarray.types.Shape;
import ai.onnxruntime.OnnxTensor;
import ai.onnxruntime.OnnxValue;
import ai.onnxruntime.OrtEnvironment;
import ai.onnxruntime.OrtException;
import ai.onnxruntime.OrtSession;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.core.io.Resource;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * https://www.sbert.net/index.html https://www.sbert.net/docs/pretrained_models.html
 *
 * @author Christian Tzolov
 */
public class TransformersEmbeddingClient implements EmbeddingClient, InitializingBean {

	private static final Log logger = LogFactory.getLog(TransformersEmbeddingClient.class);

	// ONNX tokenizer for the all-MiniLM-L6-v2 model
	public final static String DEFAULT_ONNX_TOKENIZER_URI = "https://raw.githubusercontent.com/spring-projects-experimental/spring-ai/main/embedding-clients/transformers-embedding/src/main/resources/onnx/all-MiniLM-L6-v2/tokenizer.json";

	// ONNX model for all-MiniLM-L6-v2 pre-trained transformer:
	// https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2
	public final static String DEFAULT_ONNX_MODEL_URI = "https://github.com/spring-projects-experimental/spring-ai/raw/main/embedding-clients/transformers-embedding/src/main/resources/onnx/all-MiniLM-L6-v2/model.onnx";

	private final static int EMBEDDING_AXIS = 1;

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
	 * Runtime session that wraps the ONNX model and enables inference calls.
	 */
	private OrtSession session;

	private final AtomicInteger embeddingDimensions = new AtomicInteger(-1);

	/**
	 * Specifies what parts of the {@link Document}'s content and metadata will be used
	 * for computing the embeddings. Applicable for the {@link #embed(Document)} method
	 * only. Has no effect on the {@link #embed(String)} or {@link #embed(List)}. Defaults
	 * to {@link MetadataMode#NONE}.
	 */
	private final MetadataMode metadataMode;

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

	public Map<String, String> tokenizerOptions = Map.of();

	public TransformersEmbeddingClient() {
		this(MetadataMode.NONE);
	}

	public TransformersEmbeddingClient(MetadataMode metadataMode) {
		Assert.notNull(metadataMode, "Metadata mode should not be null");
		this.metadataMode = metadataMode;
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

	public void setEmbeddingDimensions(int dimension) {
		this.embeddingDimensions.set(dimension);
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

		var sessionOptions = new OrtSession.SessionOptions();
		if (this.gpuDeviceId >= 0) {
			sessionOptions.addCUDA(this.gpuDeviceId); // Run on a GPU or with another
														// provider
		}
		this.session = this.environment.createSession(getCachedResource(this.modelResource).getContentAsByteArray(),
				sessionOptions);

		logger.info("Model input names: " + this.session.getInputNames().stream().collect(Collectors.joining(", ")));
		logger.info("Model output names: " + this.session.getOutputNames().stream().collect(Collectors.joining(", ")));
	}

	private Resource getCachedResource(Resource resource) {
		return this.disableCaching ? resource : this.cacheService.getCachedResource(resource);
	}

	@Override
	public List<Double> embed(String text) {
		return embed(List.of(text)).get(0);
	}

	@Override
	public List<Double> embed(Document document) {
		return this.embed(document.getFormattedContent(this.metadataMode));
	}

	@Override
	public EmbeddingResponse embedForResponse(List<String> texts) {
		List<Embedding> data = new ArrayList<>();
		List<List<Double>> embed = this.embed(texts);
		for (int i = 0; i < embed.size(); i++) {
			data.add(new Embedding(embed.get(i), i));
		}
		return new EmbeddingResponse(data, Map.of());
	}

	@Override
	public List<List<Double>> embed(List<String> texts) {

		List<List<Double>> resultEmbeddings = new ArrayList<>();

		try {

			Encoding[] encodings = this.tokenizer.batchEncode(texts);

			long[][] input_ids0 = new long[encodings.length][];
			long[][] attention_mask0 = new long[encodings.length][];
			long[][] token_type_ids0 = new long[encodings.length][];

			for (int i = 0; i < encodings.length; i++) {
				input_ids0[i] = encodings[i].getIds();
				attention_mask0[i] = encodings[i].getAttentionMask();
				token_type_ids0[i] = encodings[i].getTypeIds();
			}

			OnnxTensor inputIds = OnnxTensor.createTensor(this.environment, input_ids0);
			OnnxTensor attentionMask = OnnxTensor.createTensor(this.environment, attention_mask0);
			OnnxTensor tokenTypeIds = OnnxTensor.createTensor(this.environment, token_type_ids0);

			Map<String, OnnxTensor> modelInputs = Map.of("input_ids", inputIds, "attention_mask", attentionMask,
					"token_type_ids", tokenTypeIds);

			// The Run result object is AutoCloseable to prevent references from leaking
			// out. Once the Result object is
			// closed, all it’s child OnnxValues are closed too.
			try (OrtSession.Result results = this.session.run(modelInputs)) {

				// OnnxValue lastHiddenState = results.get(0);
				OnnxValue lastHiddenState = results.get("last_hidden_state").get();

				// 0 - batch_size (1..x)
				// 1 - sequence_length (128)
				// 2 - embedding dimensions (384)
				float[][][] tokenEmbeddings = (float[][][]) lastHiddenState.getValue();

				try (NDManager manager = NDManager.newBaseManager()) {
					NDArray ndTokenEmbeddings = create(tokenEmbeddings, manager);
					NDArray ndAttentionMask = manager.create(attention_mask0);

					NDArray embedding = meanPooling(ndTokenEmbeddings, ndAttentionMask);

					for (int i = 0; i < embedding.size(0); i++) {
						resultEmbeddings.add(toDoubleList(embedding.get(i).toFloatArray()));
					}
				}
			}
		}
		catch (OrtException ex) {
			throw new RuntimeException(ex);
		}

		return resultEmbeddings;
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

	private List<Double> toDoubleList(float[] floats) {
		List<Double> result = new ArrayList<>();
		if (floats != null && floats.length > 0) {
			for (int i = 0; i < floats.length; i++) {
				result.add((double) floats[i]);
			}
		}
		return result;
	}

	@Override
	public int dimensions() {
		if (this.embeddingDimensions.get() < 0) {
			this.embeddingDimensions.set(EmbeddingUtil.dimensions(this, "Test"));
		}
		return this.embeddingDimensions.get();
	}

	private static Resource toResource(String uri) {
		return new DefaultResourceLoader().getResource(uri);
	}

}
