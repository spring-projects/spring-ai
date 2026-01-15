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

	/**
	 * Configuration prefix for Redis vector store properties.
	 */
	public static final String CONFIG_PREFIX = "spring.ai.vectorstore.redis";

	/**
	 * The name of the Redis search index.
	 */
	private String indexName = "default-index";

	/**
	 * The key prefix for Redis documents.
	 */
	private String prefix = "default:";

	/**
	 * HNSW algorithm configuration properties.
	 */
	@NestedConfigurationProperty
	private HnswProperties hnsw = new HnswProperties();

	/**
	 * Returns the index name.
	 * @return the index name
	 */
	public final String getIndexName() {
		return this.indexName;
	}

	/**
	 * Sets the index name.
	 * @param name the index name
	 */
	public final void setIndexName(final String name) {
		this.indexName = name;
	}

	/**
	 * Returns the key prefix.
	 * @return the key prefix
	 */
	public final String getPrefix() {
		return this.prefix;
	}

	/**
	 * Sets the key prefix.
	 * @param keyPrefix the key prefix
	 */
	public final void setPrefix(final String keyPrefix) {
		this.prefix = keyPrefix;
	}

	/**
	 * Returns the HNSW properties.
	 * @return the HNSW properties
	 */
	public final HnswProperties getHnsw() {
		return this.hnsw;
	}

	/**
	 * Sets the HNSW properties.
	 * @param hnswProperties the HNSW properties
	 */
	public final void setHnsw(final HnswProperties hnswProperties) {
		this.hnsw = hnswProperties;
	}

	/**
	 * HNSW (Hierarchical Navigable Small World) algorithm configuration.
	 */
	public static final class HnswProperties {

		/**
		 * Default value for M parameter.
		 */
		public static final int DEFAULT_M = 16;

		/**
		 * Default value for EF_CONSTRUCTION parameter.
		 */
		public static final int DEFAULT_EF_CONSTRUCTION = 200;

		/**
		 * Default value for EF_RUNTIME parameter.
		 */
		public static final int DEFAULT_EF_RUNTIME = 10;

		/**
		 * M parameter for HNSW algorithm. Represents the maximum number of connections
		 * per node in the graph. Higher values increase recall but also memory usage.
		 * Typically between 5-100.
		 */
		private Integer m = DEFAULT_M;

		/**
		 * EF_CONSTRUCTION parameter for HNSW algorithm. Size of the dynamic candidate
		 * list during index building. Higher values lead to better recall but slower
		 * indexing. Typically between 50-500.
		 */
		private Integer efConstruction = DEFAULT_EF_CONSTRUCTION;

		/**
		 * EF_RUNTIME parameter for HNSW algorithm. Size of the dynamic candidate list
		 * during search. Higher values lead to more accurate but slower searches.
		 * Typically between 20-200.
		 */
		private Integer efRuntime = DEFAULT_EF_RUNTIME;

		/**
		 * Returns the M parameter.
		 * @return the M parameter
		 */
		public Integer getM() {
			return this.m;
		}

		/**
		 * Sets the M parameter.
		 * @param mValue the M parameter value
		 */
		public void setM(final Integer mValue) {
			this.m = mValue;
		}

		/**
		 * Returns the EF_CONSTRUCTION parameter.
		 * @return the EF_CONSTRUCTION parameter
		 */
		public Integer getEfConstruction() {
			return this.efConstruction;
		}

		/**
		 * Sets the EF_CONSTRUCTION parameter.
		 * @param construction the EF_CONSTRUCTION parameter value
		 */
		public void setEfConstruction(final Integer construction) {
			this.efConstruction = construction;
		}

		/**
		 * Returns the EF_RUNTIME parameter.
		 * @return the EF_RUNTIME parameter
		 */
		public Integer getEfRuntime() {
			return this.efRuntime;
		}

		/**
		 * Sets the EF_RUNTIME parameter.
		 * @param runtime the EF_RUNTIME parameter value
		 */
		public void setEfRuntime(final Integer runtime) {
			this.efRuntime = runtime;
		}

	}

}
