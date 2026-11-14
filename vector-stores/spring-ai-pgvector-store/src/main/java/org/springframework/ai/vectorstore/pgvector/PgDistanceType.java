/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.vectorstore.pgvector;

/**
 * Interface representing a distance type for pgvector operations.
 *
 * @author Martin Grofcik
 * @since 2.0.2
 */
public interface PgDistanceType {

	/**
	 * Returns the name of the distance type.
	 * @return the name
	 */
	String name();

	/**
	 * Returns the operator used in PostgreSQL queries.
	 * @return the operator
	 */
	String operator();

	/**
	 * Returns the index type used for the vector index.
	 * @return the index type
	 */
	String index();

	/**
	 * Returns the SQL template for similarity search queries.
	 * @return the similarity search SQL template
	 */
	String similaritySearchSqlTemplate();

}
