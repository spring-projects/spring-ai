/*
 * Copyright 2023-2025 the original author or authors.
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
package org.springframework.ai.vectorstore.redis.cache.semantic.autoconfigure;

import org.springframework.ai.chat.cache.semantic.SemanticCacheAdvisor;
import org.springframework.ai.chat.client.advisor.api.CallAdvisor;
import org.springframework.ai.chat.client.advisor.api.StreamAdvisor;
import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.ai.vectorstore.redis.cache.semantic.DefaultSemanticCache;
import org.springframework.ai.chat.cache.semantic.SemanticCache;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.data.redis.autoconfigure.DataRedisAutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.util.StringUtils;

import redis.clients.jedis.JedisPooled;

/**
 * Auto-configuration for Redis semantic cache.
 *
 * @author Brian Sam-Bodden
 */
@AutoConfiguration(after = DataRedisAutoConfiguration.class)
@ConditionalOnClass({ DefaultSemanticCache.class, JedisPooled.class, CallAdvisor.class, StreamAdvisor.class,
		TransformersEmbeddingModel.class })
@EnableConfigurationProperties(RedisSemanticCacheProperties.class)
@ConditionalOnProperty(name = "spring.ai.vectorstore.redis.semantic-cache.enabled", havingValue = "true",
		matchIfMissing = true)
public class RedisSemanticCacheAutoConfiguration {

	// URLs for the redis/langcache-embed-v1 model on HuggingFace
	private static final String LANGCACHE_TOKENIZER_URI = "https://huggingface.co/redis/langcache-embed-v1/resolve/main/tokenizer.json";

	private static final String LANGCACHE_MODEL_URI = "https://huggingface.co/redis/langcache-embed-v1/resolve/main/onnx/model.onnx";

	/**
	 * Provides a default EmbeddingModel using the redis/langcache-embed-v1 model. This
	 * model is specifically designed for semantic caching and provides 768-dimensional
	 * embeddings. It matches the default model used by RedisVL Python library.
	 */
	@Bean
	@ConditionalOnMissingBean(EmbeddingModel.class)
	@ConditionalOnClass(TransformersEmbeddingModel.class)
	public EmbeddingModel semanticCacheEmbeddingModel() throws Exception {
		TransformersEmbeddingModel model = new TransformersEmbeddingModel();
		model.setTokenizerResource(LANGCACHE_TOKENIZER_URI);
		model.setModelResource(LANGCACHE_MODEL_URI);
		model.afterPropertiesSet();
		return model;
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(EmbeddingModel.class)
	public JedisPooled jedisClient(RedisSemanticCacheProperties properties) {
		return new JedisPooled(properties.getHost(), properties.getPort());
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(EmbeddingModel.class)
	public SemanticCache semanticCache(JedisPooled jedisClient, EmbeddingModel embeddingModel,
			RedisSemanticCacheProperties properties) {
		DefaultSemanticCache.Builder builder = DefaultSemanticCache.builder()
			.jedisClient(jedisClient)
			.embeddingModel(embeddingModel);

		builder.similarityThreshold(properties.getSimilarityThreshold());

		// Apply other configuration if provided
		if (StringUtils.hasText(properties.getIndexName())) {
			builder.indexName(properties.getIndexName());
		}

		if (StringUtils.hasText(properties.getPrefix())) {
			builder.prefix(properties.getPrefix());
		}

		return builder.build();
	}

	@Bean
	@ConditionalOnMissingBean
	@ConditionalOnBean(SemanticCache.class)
	public SemanticCacheAdvisor semanticCacheAdvisor(SemanticCache semanticCache) {
		return new SemanticCacheAdvisor(semanticCache);
	}

}