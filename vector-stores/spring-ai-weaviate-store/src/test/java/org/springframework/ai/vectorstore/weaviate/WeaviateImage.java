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

package org.springframework.ai.vectorstore.weaviate;

import org.testcontainers.utility.DockerImageName;

/**
 * @author Thomas Vitale
 */
public final class WeaviateImage {

	public static final DockerImageName DEFAULT_IMAGE = DockerImageName.parse("semitechnologies/weaviate:1.25.9");

	private WeaviateImage() {

	}

}
