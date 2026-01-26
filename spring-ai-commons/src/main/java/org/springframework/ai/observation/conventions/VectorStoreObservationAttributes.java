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
 * Collection of attribute keys used in vector store observations (spans, metrics,
 * events). Based on the OpenTelemetry Semantic Conventions for Vector Databases.
 *
 * @author Thomas Vitale
 * @since 1.0.0
 * @see <a href=
 * "https://github.com/open-telemetry/semantic-conventions/tree/main/docs/database">DB
 * Semantic Conventions</a>.
 */
public enum VectorStoreObservationAttributes {

// @formatter:off

	// DB General

	/**
	 * The name of a collection (table, container) within the database.
	 */
	DB_COLLECTION_NAME("db.collection.name"),

	/**
	 * The name of the database, fully qualified within the server address and port.
	 */
	DB_NAMESPACE("db.namespace"),

	/**
	 * The name of the operation or command being executed.
	 */
	DB_OPERATION_NAME("db.operation.name"),

	/**
	 * The record identifier if present.
	 */
	DB_RECORD_ID("db.record.id"),

	/**
	 * The database management system (DBMS) product as identified by the client instrumentation.
	 */
	DB_SYSTEM("db.system"),

	// DB Search

	/**
	 * The metric used in similarity search.
	 */
	DB_SEARCH_SIMILARITY_METRIC("db.search.similarity_metric"),

	// DB Vector

	/**
	 * The dimension of the vector.
	 */
	DB_VECTOR_DIMENSION_COUNT("db.vector.dimension_count"),

	/**
	 * The name field of the vector (e.g. a field name).
	 */
	DB_VECTOR_FIELD_NAME("db.vector.field_name"),

	/**
	 * The content of the search query being executed.
	 */
	DB_VECTOR_QUERY_CONTENT("db.vector.query.content"),

	/**
	 * The metadata filters used in the search query.
	 */
	DB_VECTOR_QUERY_FILTER("db.vector.query.filter"),

	/**
	 * Returned documents from a similarity search query.
	 */
	DB_VECTOR_QUERY_RESPONSE_DOCUMENTS("db.vector.query.response.documents"),

	/**
	 * Similarity threshold that accepts all search scores. A threshold value of 0.0
	 * means any similarity is accepted or disable the similarity threshold filtering.
	 * A threshold value of 1.0 means an exact match is required.
	 */
	DB_VECTOR_QUERY_SIMILARITY_THRESHOLD("db.vector.query.similarity_threshold"),

	/**
	 * The top-k most similar vectors returned by a query.
	 */
	DB_VECTOR_QUERY_TOP_K("db.vector.query.top_k");

	private final String value;

	VectorStoreObservationAttributes(String value) {
		this.value = value;
	}

	/**
	 * Return the string value of the attribute.
	 * @return the string value of the attribute
	 */
	public String value() {
		return this.value;
	}

// @formatter:on

}
