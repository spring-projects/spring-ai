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

package org.springframework.ai.observation.conventions;

/**
 * Collection of systems providing vector store functionality. Based on the OpenTelemetry
 * Semantic Conventions for Vector Databases.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 1.0.0
 * @see <a href=
 * "https://github.com/open-telemetry/semantic-conventions/tree/main/docs/database">DB
 * Semantic Conventions</a>.
 */
public enum VectorStoreProvider {

	// @formatter:off

	// Please, keep the alphabetical sorting.
	/**
	 * Vector store provided by Azure.
	 */
	AZURE("azure"),

	/**
	 * Vector store provided by Cassandra.
	 */
	CASSANDRA("cassandra"),

	/**
	 * Vector store provided by Chroma.
	 */
	CHROMA("chroma"),

	/**
	 * Vector store provided by CosmosDB.
	 */
	COSMOSDB("cosmosdb"),
	/**
	 * Vector store provided by Couchbase.
	 */
	COUCHBASE("couchbase"),
	/**
	 * Vector store provided by Elasticsearch.
	 */
	ELASTICSEARCH("elasticsearch"),

	/**
	 * Vector store provided by GemFire.
	 */
	GEMFIRE("gemfire"),

	/**
	 * Vector store provided by HANA.
	 */
	HANA("hana"),

	/**
	 * Vector store provided by Infinispan.
	 */
	INFINISPAN("infinispan"),

	/**
	 * Vector store provided by MariaDB.
	 */
	MARIADB("mariadb"),

	/**
	 * Vector store provided by Milvus.
	 */
	MILVUS("milvus"),

	/**
	 * Vector store provided by MongoDB.
	 */
	MONGODB("mongodb"),

	/**
	 * Vector store provided by Neo4j.
	 */
	NEO4J("neo4j"),

	/**
	 * Vector store provided by OpenSearch.
	 */
	OPENSEARCH("opensearch"),

	/**
	 * Vector store provided by Oracle.
	 */
	ORACLE("oracle"),

	/**
	 * Vector store provided by PGVector.
	 */
	PG_VECTOR("pg_vector"),

	/**
	 * Vector store provided by Pinecone.
	 */
	PINECONE("pinecone"),

	/**
	 * Vector store provided by Qdrand.
	 */
	QDRANT("qdrant"),

	/**
	 * Vector store provided by Redis.
	 */
	REDIS("redis"),

	/**
	 * Vector store provided by simple.
	 */
	SIMPLE("simple"),

	/**
	 * Vector store provided by Typesense.
	 */
	TYPESENSE("typesense"),

	/**
	 * Vector store provided by Weaviate.
	 */
	WEAVIATE("weaviate");

	// @formatter:on

	private final String value;

	VectorStoreProvider(String value) {
		this.value = value;
	}

	/**
	 * Return the value of the vector store provider.
	 * @return the value of the vector store provider
	 */
	public String value() {
		return this.value;
	}

}
