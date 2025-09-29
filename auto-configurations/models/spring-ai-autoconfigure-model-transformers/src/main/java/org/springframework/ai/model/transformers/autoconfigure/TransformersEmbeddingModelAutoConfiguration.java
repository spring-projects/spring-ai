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

import ai.djl.huggingface.tokenizers.HuggingFaceTokenizer;
import ai.onnxruntime.OrtSession;
import io.micrometer.observation.ObservationRegistry;

import org.springframework.ai.embedding.observation.EmbeddingModelObservationConvention;
import org.springframework.ai.model.SpringAIModelProperties;
import org.springframework.ai.model.SpringAIModels;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * {@link AutoConfiguration Auto-configuration} for Transformers Embedding Model.
 *
 * @author Christian Tzolov
 */
@AutoConfiguration
@EnableConfigurationProperties({ TransformersEmbeddingModelProperties.class })
@ConditionalOnProperty(name = SpringAIModelProperties.EMBEDDING_MODEL, havingValue = SpringAIModels.TRANSFORMERS,
		matchIfMissing = true)
@ConditionalOnClass({ OrtSession.class, HuggingFaceTokenizer.class, TransformersEmbeddingModel.class })
public class TransformersEmbeddingModelAutoConfiguration {

	@Bean
	@ConditionalOnMissingBean
	public TransformersEmbeddingModel embeddingModel(TransformersEmbeddingModelProperties properties,
			ObjectProvider<ObservationRegistry> observationRegistry,
			ObjectProvider<EmbeddingModelObservationConvention> observationConvention) {

		TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel(properties.getMetadataMode(),
				observationRegistry.getIfUnique(() -> ObservationRegistry.NOOP));

		embeddingModel.setDisableCaching(!properties.getCache().isEnabled());
		embeddingModel.setResourceCacheDirectory(properties.getCache().getDirectory());

		embeddingModel.setTokenizerResource(properties.getTokenizer().getUri());
		embeddingModel.setTokenizerOptions(properties.getTokenizer().getOptions());

		embeddingModel.setModelResource(properties.getOnnx().getModelUri());

		embeddingModel.setGpuDeviceId(properties.getOnnx().getGpuDeviceId());

		embeddingModel.setModelOutputName(properties.getOnnx().getModelOutputName());

		observationConvention.ifAvailable(embeddingModel::setObservationConvention);

		return embeddingModel;
	}

}
