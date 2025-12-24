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

package org.springframework.ai.vectorstore;

public final class SpringAIVectorStoreTypes {

	private SpringAIVectorStoreTypes() {
		// Avoids instantiation
	}

	public static final String VECTOR_STORE_PREFIX = "spring.ai.vectorstore";

	public static final String TYPE = VECTOR_STORE_PREFIX + ".type";

	public static final String AZURE = "azure";

	public static final String AZURE_COSMOS_DB = "azure-cosmos-db";

	public static final String CASSANDRA = "cassandra";

	public static final String CHROMA = "chroma";

	public static final String ELASTICSEARCH = "elasticsearch";

	public static final String GEMFIRE = "gemfire";

	public static final String HANADB = "hanadb";

	public static final String INFINISPAN = "infinispan";

	public static final String MARIADB = "mariadb";

	public static final String MILVUS = "milvus";

	public static final String MONGODB_ATLAS = "mongodb-atlas";

	public static final String NEO4J = "neo4j";

	public static final String OPENSEARCH = "opensearch";

	public static final String ORACLE = "oracle";

	public static final String PGVECTOR = "pgvector";

	public static final String PINECONE = "pinecone";

	public static final String QDRANT = "qdrant";

	public static final String REDIS = "redis";

	public static final String TYPESENSE = "typesense";

	public static final String WEAVIATE = "weaviate";

}
