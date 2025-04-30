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

package org.springframework.ai.model.transformers.autoconfigure;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;

import org.springframework.ai.document.Document;
import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for the Transformer Embedding model.
 *
 * @author Christian Tzolov
 */
@ConfigurationProperties(TransformersEmbeddingModelProperties.CONFIG_PREFIX)
public class TransformersEmbeddingModelProperties {

	public static final String CONFIG_PREFIX = "spring.ai.embedding.transformer";

	public static final String DEFAULT_CACHE_DIRECTORY = new File(System.getProperty("java.io.tmpdir"),
			"spring-ai-onnx-generative")
		.getAbsolutePath();

	@NestedConfigurationProperty
	private final Tokenizer tokenizer = new Tokenizer();

	/**
	 * Controls caching of remote, large resources to local file system.
	 */
	@NestedConfigurationProperty
	private final Cache cache = new Cache();

	@NestedConfigurationProperty
	private final Onnx onnx = new Onnx();

	/**
	 * Specifies what parts of the {@link Document}'s content and metadata will be used
	 * for computing the embeddings. Applicable for the
	 * {@link TransformersEmbeddingModel#embed(Document)} method only. Has no effect on
	 * the {@link TransformersEmbeddingModel#embed(String)} or
	 * {@link TransformersEmbeddingModel#embed(List)}. Defaults to
	 * {@link MetadataMode#NONE}.
	 */
	private MetadataMode metadataMode = MetadataMode.NONE;

	public Cache getCache() {
		return this.cache;
	}

	public Onnx getOnnx() {
		return this.onnx;
	}

	public Tokenizer getTokenizer() {
		return this.tokenizer;
	}

	public MetadataMode getMetadataMode() {
		return this.metadataMode;
	}

	public void setMetadataMode(MetadataMode metadataMode) {
		this.metadataMode = metadataMode;
	}

	/**
	 * Configurations for the {@link HuggingFaceTokenizer} used to convert sentences into
	 * tokens.
	 */
	public static class Tokenizer {

		/**
		 * URI of a pre-trained HuggingFaceTokenizer created by the ONNX engine (e.g.
		 * tokenizer.json).
		 */
		private String uri = TransformersEmbeddingModel.DEFAULT_ONNX_TOKENIZER_URI;

		/**
		 * HuggingFaceTokenizer options such as 'addSpecialTokens', 'modelMaxLength',
		 * 'truncation', 'padding', 'maxLength', 'stride' and 'padToMultipleOf'. Leave
		 * empty to fall back to the defaults.
		 */
		@NestedConfigurationProperty
		private Map<String, String> options = new HashMap<>();

		public String getUri() {
			return this.uri;
		}

		public void setUri(String uri) {
			this.uri = uri;
		}

		public Map<String, String> getOptions() {
			return this.options;
		}

		public void setOptions(Map<String, String> options) {
			this.options = options;
		}

	}

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
			return this.enabled;
		}

		public void setEnabled(boolean enabled) {
			this.enabled = enabled;
		}

		public String getDirectory() {
			return this.directory;
		}

		public void setDirectory(String directory) {
			this.directory = directory;
		}

	}

	public static class Onnx {

		/**
		 * Existing, pre-trained ONNX generative. Commonly exported from
		 * https://sbert.net/docs/pretrained_models.html. Defaults to
		 * sentence-transformers/all-MiniLM-L6-v2.
		 */
		private String modelUri = TransformersEmbeddingModel.DEFAULT_ONNX_MODEL_URI;

		/**
		 * Defaults to: 'last_hidden_state'.
		 */
		private String modelOutputName = TransformersEmbeddingModel.DEFAULT_MODEL_OUTPUT_NAME;

		/**
		 * Run on a GPU or with another provider (optional).
		 * https://onnxruntime.ai/docs/get-started/with-java.html#run-on-a-gpu-or-with-another-provider-optional
		 *
		 * The GPU device ID to execute on. Only applicable if >= 0. Ignored otherwise.
		 */
		private int gpuDeviceId = -1;

		public String getModelUri() {
			return this.modelUri;
		}

		public void setModelUri(String modelUri) {
			this.modelUri = modelUri;
		}

		public int getGpuDeviceId() {
			return this.gpuDeviceId;
		}

		public void setGpuDeviceId(int gpuDeviceId) {
			this.gpuDeviceId = gpuDeviceId;
		}

		public String getModelOutputName() {
			return this.modelOutputName;
		}

		public void setModelOutputName(String modelOutputName) {
			this.modelOutputName = modelOutputName;
		}

	}

}
