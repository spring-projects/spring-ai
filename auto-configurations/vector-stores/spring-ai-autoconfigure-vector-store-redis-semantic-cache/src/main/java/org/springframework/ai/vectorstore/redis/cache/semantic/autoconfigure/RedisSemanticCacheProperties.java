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

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Configuration properties for Redis semantic cache.
 *
 * @author Brian Sam-Bodden
 * @author Eddú Meléndez
 */
@ConfigurationProperties(prefix = "spring.ai.vectorstore.redis.semantic-cache")
public class RedisSemanticCacheProperties {

	/**
	 * Enable the Redis semantic cache.
	 */
	private boolean enabled = true;

	/**
	 * Similarity threshold for matching cached responses (0.0 to 1.0). Higher values mean
	 * stricter matching.
	 */
	private double similarityThreshold = 0.95;

	/**
	 * Name of the Redis search index.
	 */
	private String indexName = "semantic-cache-index";

	/**
	 * Key prefix for Redis semantic cache entries.
	 */
	private String prefix = "semantic-cache:";

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public double getSimilarityThreshold() {
		return similarityThreshold;
	}

	public void setSimilarityThreshold(double similarityThreshold) {
		this.similarityThreshold = similarityThreshold;
	}

	public String getIndexName() {
		return indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public String getPrefix() {
		return prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

}