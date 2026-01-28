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

package org.springframework.ai.vectorstore.gemfire;

import org.junit.jupiter.api.Disabled;

import org.springframework.ai.embedding.EmbeddingModel;
import org.springframework.ai.transformers.TransformersEmbeddingModel;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.context.annotation.Bean;

/**
 * @author Jason Huynh
 */
@Disabled
public class GemFireWithTokenAuthenticationVectorStoreIT extends GemFireVectorStoreAuthenticationBaseIT {

	@Override
	Class getTestApplicationClass() {
		return TestApplication.class;
	}

	@SpringBootConfiguration
	@EnableAutoConfiguration
	public static class TestApplication {

		@Bean
		public GemFireVectorStore vectorStore(EmbeddingModel embeddingModel) {
			return GemFireVectorStore.builder(embeddingModel)
				.host("localhost")
				.port(HTTP_SERVICE_PORT)
				.token("01234567890123456789012345678901234567890")
				.indexName(INDEX_NAME)
				.fields(new String[] { "year", "country", "activationDate" })
				.initializeSchema(true)
				.build();
		}

		@Bean
		public EmbeddingModel embeddingModel() {
			return new TransformersEmbeddingModel();
		}

	}

}
