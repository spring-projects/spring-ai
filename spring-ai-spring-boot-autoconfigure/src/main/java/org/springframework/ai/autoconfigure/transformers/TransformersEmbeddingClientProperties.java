/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.autoconfigure.transformers;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.transformers.TransformersEmbeddingClient;
import org.springframework.boot.context.properties.ConfigurationProperties;

import static org.springframework.ai.autoconfigure.transformers.TransformersEmbeddingClientProperties.CONFIG_PREFIX;

/**
 * @author Christian Tzolov
 */
@ConfigurationProperties(CONFIG_PREFIX)
public class TransformersEmbeddingClientProperties {

	public static final String CONFIG_PREFIX = "spring.ai.embedding.transformer";

	public static final String DEFAULT_CACHE_DIRECTORY = new File(System.getProperty("java.io.tmpdir"),
			"spring-ai-onnx-generative")
		.getAbsolutePath();

	/**
	 * Configurations for the {@link HuggingFaceTokenizer} used to convert sentences into
	 * tokens.
	 */
	public static class Tokenizer {

		/**
		 * URI of a pre-trained HuggingFaceTokenizer created by the ONNX engine (e.g.
		 * tokenizer.json).
		 */
		private String uri = TransformersEmbeddingClient.DEFAULT_ONNX_TOKENIZER_URI;

		/**
		 * HuggingFaceTokenizer options such as 'addSpecialTokens', 'modelMaxLength',
		 * 'truncation', 'padding', 'maxLength', 'stride' and 'padToMultipleOf'. Leave
		 * empty to fall back to the defaults.
		 */
		private Map<String, String> options = new HashMap<>();

		public String getUri() {
			return uri;
		}

		public void setUri(String uri) {
			this.uri = uri;
		}

		public Map<String, String> getOptions() {
			return options;
		}

		public void setOptions(Map<String, String> options) {
			this.options = options;
		}

	}

	private final Tokenizer tokenizer = new Tokenizer();

	public static class Cache {

		/**
		 * Enable the Resource caching.
		 */
		private boolean enabled = true;

		/**
		 * Resource cache directory. Used to cache remote resources, such as the ONNX
		 * models, to the local file system. Applicable only for cache.enabled == true.
		 * Defaults to {java.io.tmpdir}/spring-ai-onnx-generative.
		 */
		private String directory = DEFAULT_CACHE_DIRECTORY;

		public boolean isEnabled() {
			return enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getDirectory() {
			return directory;
		}

		public void setDirectory(String directory) {
			this.directory = directory;
		}

	}

	/**
	 * Controls caching of remote, large resources to local file system.
	 */
	private final Cache cache = new Cache();

	public Cache getCache() {
		return cache;
	}

	public static class Onnx {

		/**
		 * Existing, pre-trained ONNX generative. Commonly exported from
		 * https://sbert.net/docs/pretrained_models.html. Defaults to
		 * sentence-transformers/all-MiniLM-L6-v2.
		 */
		private String modelUri = TransformersEmbeddingClient.DEFAULT_ONNX_MODEL_URI;

		/**
		 * Defaults to: 'last_hidden_state'.
		 */
		private String modelOutputName = TransformersEmbeddingClient.DEFAULT_MODEL_OUTPUT_NAME;

		/**
		 * Run on a GPU or with another provider (optional).
		 * https://onnxruntime.ai/docs/get-started/with-java.html#run-on-a-gpu-or-with-another-provider-optional
		 *
		 * The GPU device ID to execute on. Only applicable if >= 0. Ignored otherwise.
		 */
		private int gpuDeviceId = -1;

		public String getModelUri() {
			return modelUri;
		}

		public void setModelUri(String modelUri) {
			this.modelUri = modelUri;
		}

		public int getGpuDeviceId() {
			return gpuDeviceId;
		}

		public void setGpuDeviceId(int gpuDeviceId) {
			this.gpuDeviceId = gpuDeviceId;
		}

		public String getModelOutputName() {
			return modelOutputName;
		}

		public void setModelOutputName(String modelOutputName) {
			this.modelOutputName = modelOutputName;
		}

	}

	private final Onnx onnx = new Onnx();

	public Onnx getOnnx() {
		return onnx;
	}

	/**
	 * Specifies what parts of the {@link Document}'s content and metadata will be used
	 * for computing the embeddings. Applicable for the
	 * {@link TransformersEmbeddingClient#embed(Document)} method only. Has no effect on
	 * the {@link TransformersEmbeddingClient#embed(String)} or
	 * {@link TransformersEmbeddingClient#embed(List)}. Defaults to
	 * {@link MetadataMode#NONE}.
	 */
	private MetadataMode metadataMode = MetadataMode.NONE;

	public Tokenizer getTokenizer() {
		return tokenizer;
	}

	public MetadataMode getMetadataMode() {
		return metadataMode;
	}

	public void setMetadataMode(MetadataMode metadataMode) {
		this.metadataMode = metadataMode;
	}

}
