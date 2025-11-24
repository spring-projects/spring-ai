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

package org.springframework.ai.vectorstore.redis.autoconfigure;

import org.springframework.ai.vectorstore.properties.CommonVectorStoreProperties;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.NestedConfigurationProperty;

/**
 * Configuration properties for Redis Vector Store.
 *
 * <p>
 * Example application.properties:
 * </p>
 * <pre>
 * spring.ai.vectorstore.redis.index-name=my-index
 * spring.ai.vectorstore.redis.prefix=doc:
 * spring.ai.vectorstore.redis.initialize-schema=true
 *
 * # HNSW algorithm configuration
 * spring.ai.vectorstore.redis.hnsw.m=32
 * spring.ai.vectorstore.redis.hnsw.ef-construction=100
 * spring.ai.vectorstore.redis.hnsw.ef-runtime=50
 * </pre>
 *
 * @author Julien Ruaux
 * @author Eddú Meléndez
 * @author Brian Sam-Bodden
 */
@ConfigurationProperties(RedisVectorStoreProperties.CONFIG_PREFIX)
public class RedisVectorStoreProperties extends CommonVectorStoreProperties {

	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.redis";

	private String indexName = "default-index";

	private String prefix = "default:";

	/**
	 * HNSW algorithm configuration properties.
	 */
	@NestedConfigurationProperty
	private HnswProperties hnsw = new HnswProperties();

	public String getIndexName() {
		return this.indexName;
	}

	public void setIndexName(String indexName) {
		this.indexName = indexName;
	}

	public String getPrefix() {
		return this.prefix;
	}

	public void setPrefix(String prefix) {
		this.prefix = prefix;
	}

	public HnswProperties getHnsw() {
		return this.hnsw;
	}

	public void setHnsw(HnswProperties hnsw) {
		this.hnsw = hnsw;
	}

	/**
	 * HNSW (Hierarchical Navigable Small World) algorithm configuration properties.
	 */
	public static class HnswProperties {

		/**
		 * M parameter for HNSW algorithm. Represents the maximum number of connections
		 * per node in the graph. Higher values increase recall but also memory usage.
		 * Typically between 5-100. Default: 16
		 */
		private Integer m = 16;

		/**
		 * EF_CONSTRUCTION parameter for HNSW algorithm. Size of the dynamic candidate
		 * list during index building. Higher values lead to better recall but slower
		 * indexing. Typically between 50-500. Default: 200
		 */
		private Integer efConstruction = 200;

		/**
		 * EF_RUNTIME parameter for HNSW algorithm. Size of the dynamic candidate list
		 * during search. Higher values lead to more accurate but slower searches.
		 * Typically between 20-200. Default: 10
		 */
		private Integer efRuntime = 10;

		public Integer getM() {
			return this.m;
		}

		public void setM(Integer m) {
			this.m = m;
		}

		public Integer getEfConstruction() {
			return this.efConstruction;
		}

		public void setEfConstruction(Integer efConstruction) {
			this.efConstruction = efConstruction;
		}

		public Integer getEfRuntime() {
			return this.efRuntime;
		}

		public void setEfRuntime(Integer efRuntime) {
			this.efRuntime = efRuntime;
		}

	}

}
