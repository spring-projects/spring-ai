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

package org.springframework.ai.oci;

public class BaseEmbeddingModelTest extends BaseOCIGenAITest {

	public static final String EMBEDDING_MODEL_V2 = "cohere.embed-english-light-v2.0";

	public static final String EMBEDDING_MODEL_V3 = "cohere.embed-english-v3.0";

	/**
	 * Create an OCIEmbeddingModel instance using a config file authentication provider.
	 * @return OCIEmbeddingModel instance
	 */
	public static OCIEmbeddingModel getEmbeddingModel() {
		OCIEmbeddingOptions options = OCIEmbeddingOptions.builder()
			.model(EMBEDDING_MODEL_V2)
			.compartment(COMPARTMENT_ID)
			.servingMode("on-demand")
			.build();
		return new OCIEmbeddingModel(getGenerativeAIClient(), options);
	}

}
