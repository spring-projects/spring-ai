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
	AZURE("azure"),
	CASSANDRA("cassandra"),
	CHROMA("chroma"),
	COSMOSDB("cosmosdb"),
	ELASTICSEARCH("elasticsearch"),
	GEMFIRE("gemfire"),
	HANA("hana"),
	MILVUS("milvus"),
	MONGODB("mongodb"),
	NEO4J("neo4j"),
	OPENSEARCH("opensearch"),
	ORACLE("oracle"),
	PG_VECTOR("pg_vector"),
	PINECONE("pinecone"),
	QDRANT("qdrant"),
	REDIS("redis"),
	SIMPLE("simple"),
	TYPESENSE("typesense"),
	WEAVIATE("weaviate");

	// @formatter:on

	private final String value;

	VectorStoreProvider(String value) {
		this.value = value;
	}

	public String value() {
		return this.value;
	}

}
