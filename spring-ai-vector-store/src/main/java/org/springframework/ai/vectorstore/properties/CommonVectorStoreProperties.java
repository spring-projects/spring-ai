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

package org.springframework.ai.vectorstore.properties;

/**
 * Common properties for vector stores.
 *
 * @author Josh Long
 * @author Soby Chacko
 */
public class CommonVectorStoreProperties {

	/**
	 * Vector stores do not initialize schema by default on application startup. The
	 * applications explicitly need to opt-in for initializing the schema on startup. The
	 * recommended way to initialize the schema on startup is to set the initialize-schema
	 * property on the vector store. See {@link #setInitializeSchema(boolean)}.
	 */
	private boolean initializeSchema = false;

	public boolean isInitializeSchema() {
		return this.initializeSchema;
	}

	public void setInitializeSchema(boolean initializeSchema) {
		this.initializeSchema = initializeSchema;
	}

}
