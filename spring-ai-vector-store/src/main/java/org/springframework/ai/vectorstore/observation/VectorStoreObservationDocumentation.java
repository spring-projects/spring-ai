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

package org.springframework.ai.vectorstore.observation;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

import org.springframework.ai.observation.conventions.VectorStoreObservationAttributes;

/**
 * Documented conventions for vector store observations.
 *
 * @author Christian Tzolov
 * @author Thomas Vitale
 * @since 1.0.0
 */
public enum VectorStoreObservationDocumentation implements ObservationDocumentation {

	/**
	 * Vector Store observations for clients.
	 */
	AI_VECTOR_STORE {
		@Override
		public Class<? extends ObservationConvention<? extends Observation.Context>> getDefaultConvention() {
			return DefaultVectorStoreObservationConvention.class;
		}

		@Override
		public KeyName[] getLowCardinalityKeyNames() {
			return LowCardinalityKeyNames.values();
		}

		@Override
		public KeyName[] getHighCardinalityKeyNames() {
			return HighCardinalityKeyNames.values();
		}
	};

	/**
	 * Low-cardinality observation key names for vector store operations.
	 */
	public enum LowCardinalityKeyNames implements KeyName {

		/**
		 * Spring AI kind.
		 */
		SPRING_AI_KIND {
			@Override
			public String asString() {
				return "spring.ai.kind";
			}
		},

		/**
		 * The name of the operation or command being executed.
		 */
		DB_OPERATION_NAME {
			@Override
			public String asString() {
				return VectorStoreObservationAttributes.DB_OPERATION_NAME.value();
			}
		},

		/**
		 * The database management system (DBMS) product as identified by the client
		 * instrumentation.
		 */
		DB_SYSTEM {
			@Override
			public String asString() {
				return VectorStoreObservationAttributes.DB_SYSTEM.value();
			}
		}

	}

	/**
	 * High-cardinality observation key names for vector store operations.
	 */
	public enum HighCardinalityKeyNames implements KeyName {

		// DB General

		/**
		 * The name of a collection (table, container) within the database.
		 */
		DB_COLLECTION_NAME {
			@Override
			public String asString() {
				return VectorStoreObservationAttributes.DB_COLLECTION_NAME.value();
			}
		},

		/**
		 * The namespace of the database.
		 */
		DB_NAMESPACE {
			@Override
			public String asString() {
				return VectorStoreObservationAttributes.DB_NAMESPACE.value();
			}
		},

		// DB Search

		/**
		 * The metric used in similarity search.
		 */
		DB_SEARCH_SIMILARITY_METRIC {
			@Override
			public String asString() {
				return VectorStoreObservationAttributes.DB_SEARCH_SIMILARITY_METRIC.value();
			}
		},

		// DB Vector

		/**
		 * The dimension of the vector.
		 */
		DB_VECTOR_DIMENSION_COUNT {
			@Override
			public String asString() {
				return VectorStoreObservationAttributes.DB_VECTOR_DIMENSION_COUNT.value();
			}
		},

		/**
		 * The name field as of the vector (e.g. a field name).
		 */
		DB_VECTOR_FIELD_NAME {
			@Override
			public String asString() {
				return VectorStoreObservationAttributes.DB_VECTOR_FIELD_NAME.value();
			}
		},

		/**
		 * The content of the search query being executed.
		 */
		DB_VECTOR_QUERY_CONTENT {
			@Override
			public String asString() {
				return VectorStoreObservationAttributes.DB_VECTOR_QUERY_CONTENT.value();
			}
		},

		/**
		 * The metadata filters used in the search query.
		 */
		DB_VECTOR_QUERY_FILTER {
			@Override
			public String asString() {
				return "db.vector.query.filter";
			}
		},

		/**
		 * Similarity threshold that accepts all search scores. A threshold value of 0.0
		 * means any similarity is accepted or disable the similarity threshold filtering.
		 * A threshold value of 1.0 means an exact match is required.
		 */
		DB_VECTOR_QUERY_SIMILARITY_THRESHOLD {
			@Override
			public String asString() {
				return VectorStoreObservationAttributes.DB_VECTOR_QUERY_SIMILARITY_THRESHOLD.value();
			}
		},

		/**
		 * The top-k most similar vectors returned by a query.
		 */
		DB_VECTOR_QUERY_TOP_K {
			@Override
			public String asString() {
				return VectorStoreObservationAttributes.DB_VECTOR_QUERY_TOP_K.value();
			}
		}

	}

}
