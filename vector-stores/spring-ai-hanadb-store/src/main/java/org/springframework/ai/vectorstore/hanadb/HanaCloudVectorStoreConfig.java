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

package org.springframework.ai.vectorstore.hanadb;

import org.springframework.ai.embedding.EmbeddingModel;

/**
 * The {@code HanaCloudVectorStoreConfig} class represents the configuration for the
 * HanaCloudVectorStore. It provides methods to retrieve the table name and the topK
 * value.
 *
 * @author Rahul Mittal
 * @since 1.0.0
 * @deprecated Since 1.0.0-M5, use
 * {@link HanaCloudVectorStore#builder(HanaVectorRepository, EmbeddingModel)}
 */
@Deprecated(since = "1.0.0-M5", forRemoval = true)
public final class HanaCloudVectorStoreConfig {

	private String tableName;

	private int topK;

	private HanaCloudVectorStoreConfig() {
	}

	/**
	 * Creates a new builder for HanaCloudVectorStoreConfig.
	 * @return a new builder instance
	 * @deprecated Since 1.0.0-M5, use
	 * {@link HanaCloudVectorStore#builder(HanaVectorRepository, EmbeddingModel)}
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public static HanaCloudVectorStoreConfigBuilder builder() {
		return new HanaCloudVectorStoreConfigBuilder();
	}

	/**
	 * @deprecated Since 1.0.0-M5, use
	 * {@link HanaCloudVectorStore#builder(HanaVectorRepository, EmbeddingModel)}
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public String getTableName() {
		return this.tableName;
	}

	/**
	 * @deprecated Since 1.0.0-M5, use
	 * {@link HanaCloudVectorStore#builder(HanaVectorRepository, EmbeddingModel)}
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public int getTopK() {
		return this.topK;
	}

	/**
	 * @deprecated Since 1.0.0-M5, use
	 * {@link HanaCloudVectorStore#builder(HanaVectorRepository, EmbeddingModel)}
	 */
	@Deprecated(since = "1.0.0-M5", forRemoval = true)
	public static class HanaCloudVectorStoreConfigBuilder {

		private String tableName;

		private int topK;

		/**
		 * @deprecated Since 1.0.0-M5, use
		 * {@link HanaCloudVectorStore#builder(HanaVectorRepository, EmbeddingModel)}
		 */
		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public HanaCloudVectorStoreConfigBuilder tableName(String tableName) {
			this.tableName = tableName;
			return this;
		}

		/**
		 * @deprecated Since 1.0.0-M5, use
		 * {@link HanaCloudVectorStore#builder(HanaVectorRepository, EmbeddingModel)}
		 */
		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public HanaCloudVectorStoreConfigBuilder topK(int topK) {
			this.topK = topK;
			return this;
		}

		/**
		 * @deprecated Since 1.0.0-M5, use
		 * {@link HanaCloudVectorStore#builder(HanaVectorRepository, EmbeddingModel)}
		 */
		@Deprecated(since = "1.0.0-M5", forRemoval = true)
		public HanaCloudVectorStoreConfig build() {
			HanaCloudVectorStoreConfig config = new HanaCloudVectorStoreConfig();
			config.tableName = this.tableName;
			config.topK = this.topK;
			return config;
		}

	}

}
