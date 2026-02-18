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

package org.springframework.ai.chroma.vectorstore.common;

import org.springframework.ai.chroma.vectorstore.ChromaVectorStore.ChromaDistanceType;

/**
 * Common value constants for Chroma api.
 *
 * @author Jonghoon Park
 */
public final class ChromaApiConstants {

	public static final String DEFAULT_BASE_URL = "http://localhost:8000";

	public static final String DEFAULT_TENANT_NAME = "SpringAiTenant";

	public static final String DEFAULT_DATABASE_NAME = "SpringAiDatabase";

	public static final String DEFAULT_COLLECTION_NAME = "SpringAiCollection";

	public static final int DEFAULT_EF_CONSTRUCTION = 100;

	public static final int DEFAULT_EF_SEARCH = 100;

	public static final ChromaDistanceType DEFAULT_DISTANCE_TYPE = ChromaDistanceType.COSINE;

	private ChromaApiConstants() {
		// prevents instantiation.
	}

}
