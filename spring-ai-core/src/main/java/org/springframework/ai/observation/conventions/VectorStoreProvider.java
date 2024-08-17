/*
* Copyright 2024 - 2024 the original author or authors.
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
package org.springframework.ai.observation.conventions;

/**
 * @author Christian Tzolov
 * @since 1.0.0
 */
public enum VectorStoreProvider {

	// @formatter:off
        PG_VECTOR("pg_vector"),
        AZURE_VECTOR_STORE("azure_vector_store"),
        CASSANDRA_VECTOR_STORE("cassandra_vector_store"),
        CHROMA_VECTOR_STORE("chroma_vector_store"),
        ELASTICSEARCH_VECTOR_STORE("elasticsearch_vector_store"),
        MILVUS_VECTOR_STORE("milvus_vector_store"),
        NEO4J_VECTOR_STORE("neo4j_vector_store"),
        OPENSEARCH_VECTOR_STORE("opensearch_vector_store"),
        QDRANT_VECTOR_STORE("qdrant_vector_store"),
        REDIS_VECTOR_STORE("redis_vector_store"),
        TYPESENSE_VECTOR_STORE("typesense_vector_store"),
        WEAVIATE_VECTOR_STORE("weaviate_vector_store"),
        PINECONE_VECTOR_STORE("pinecone_vector_store"),
        ORACLE_VECTOR_STORE("oracle_vector_store"),
        MONGODB_VECTOR_STORE("mongodb_vector_store"),
        GEMFIRE_VECTOR_STORE("gemfire_vector_store"),
        HANA_VECTOR_STORE("hana_vector_store"),
        SIMPLE_VECTOR_STORE("simple_vector_store");

        // @formatter:on
	private final String value;

	VectorStoreProvider(String value) {
		this.value = value;
	}

	public String value() {
		return this.value;
	}

}
