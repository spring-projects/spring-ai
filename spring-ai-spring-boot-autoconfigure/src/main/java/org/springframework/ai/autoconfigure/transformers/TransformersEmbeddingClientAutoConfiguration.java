/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.autoconfigure.transformers;

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OrtSession;

import org.springframework.ai.embedding.EmbeddingClient;
import org.springframework.ai.transformers.TransformersEmbeddingClient;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * @author Christian Tzolov
 */
@AutoConfiguration
@EnableConfigurationProperties({ TransformersEmbeddingClientProperties.class })
@ConditionalOnClass({ OrtSession.class, HuggingFaceTokenizer.class })
public class TransformersEmbeddingClientAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnProperty(prefix = TransformersEmbeddingClientProperties.CONFIG_PREFIX, name = "enabled",
			havingValue = "true", matchIfMissing = true)
	public TransformersEmbeddingClient embeddingClient(TransformersEmbeddingClientProperties properties) {

		TransformersEmbeddingClient embeddingClient = new TransformersEmbeddingClient(properties.getMetadataMode());

		embeddingClient.setDisableCaching(!properties.getCache().isEnabled());
		embeddingClient.setResourceCacheDirectory(properties.getCache().getDirectory());

		embeddingClient.setTokenizerResource(properties.getTokenizer().getUri());
		embeddingClient.setTokenizerOptions(properties.getTokenizer().getOptions());

		embeddingClient.setModelResource(properties.getOnnx().getModelUri());

		embeddingClient.setGpuDeviceId(properties.getOnnx().getGpuDeviceId());

		return embeddingClient;
	}

}
