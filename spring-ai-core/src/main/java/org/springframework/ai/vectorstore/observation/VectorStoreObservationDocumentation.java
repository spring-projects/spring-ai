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
package org.springframework.ai.vectorstore.observation;

import org.springframework.ai.observation.conventions.AiObservationAttributes;

import io.micrometer.common.docs.KeyName;
import io.micrometer.observation.Observation;
import io.micrometer.observation.ObservationConvention;
import io.micrometer.observation.docs.ObservationDocumentation;

/**
 * @author Christian Tzolov
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
				return AiObservationAttributes.DB_OPERATION_NAME.value();
			}
		},
		/**
		 * The database management system (DBMS) product as identified by the client
		 * instrumentation.
		 */
		DB_SYSTEM {
			@Override
			public String asString() {
				return "db.system";
			}
		};

	}

	public enum HighCardinalityKeyNames implements KeyName {

		/**
		 * Similarity search response content.
		 */
		QUERY_RESPONSE {
			@Override
			public String asString() {
				return "db.vector.query.response.documents";
			}
		},
		/**
		 * The database query being executed.
		 */
		QUERY {
			@Override
			public String asString() {
				return "db.vector.query.content";
			}
		},
		/**
		 * The metadata filters used in the query.
		 */
		QUERY_METADATA_FILTER {
			@Override
			public String asString() {
				return "db.vector.query.filter";
			}
		},
		/**
		 * The metric used in similarity search.
		 */
		SIMILARITY_METRIC {
			@Override
			public String asString() {
				return "db.vector.similarity_metric";
			}
		},
		/**
		 * The top-k most similar vectors returned by a query.
		 */
		TOP_K {
			@Override
			public String asString() {
				return "db.vector.query.top_k";
			}
		},
		/**
		 * The dimension of the vector.
		 */
		DIMENSIONS {
			@Override
			public String asString() {
				return "db.vector.dimension_count";
			}
		},
		/**
		 * The name field as of the vector (e.g. a field name).
		 */
		FIELD_NAME {
			@Override
			public String asString() {
				return "db.vector.name";
			}
		},
		/**
		 * The name of a collection (table, container) within the database.
		 */
		COLLECTION_NAME {
			@Override
			public String asString() {
				return "db.collection.name";
			}
		},
		/**
		 * The namespace of the database.
		 */
		NAMESPACE {
			@Override
			public String asString() {
				return "db.namespace";
			}
		},
		/**
		 * The index name used in the query.
		 */
		INDEX_NAME {
			@Override
			public String asString() {
				return "db.index.name";
			}
		}

	}

}