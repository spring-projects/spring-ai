/*
 * Copyright 2024 the original author or authors.
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

package org.springframework.ai.autoconfigure.postgresml;

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.MetadataMode;
import org.springframework.ai.postgresml.PostgresMlEmbeddingClient;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit Tests for {@link PostgresMlProperties}.
 *
 * @author Utkarsh Srivastava
 */
@SpringBootTest(properties = { "spring.ai.postgresml.metadata-mode=all", "spring.ai.postgresml.kwargs.key1=value1",
		"spring.ai.postgresml.kwargs.key2=value2", "spring.ai.postgresml.embedding.transformer=abc123" })
class PostgresMlPropertiesTests {

	@Autowired
	private PostgresMlProperties postgresMlProperties;

	@Test
	void postgresMlPropertiesAreCorrect() {
		assertThat(this.postgresMlProperties).isNotNull();
		assertThat(this.postgresMlProperties.getTransformer()).isEqualTo("distilbert-base-uncased");
		assertThat(this.postgresMlProperties.getVectorType()).isEqualTo(PostgresMlEmbeddingClient.VectorType.PG_ARRAY);
		assertThat(this.postgresMlProperties.getKwargs()).isEqualTo(Map.of("key1", "value1", "key2", "value2"));
		assertThat(this.postgresMlProperties.getMetadataMode()).isEqualTo(MetadataMode.ALL);

		PostgresMlProperties.Embedding embedding = this.postgresMlProperties.getEmbedding();

		assertThat(embedding).isNotNull();
		assertThat(embedding.getTransformer()).isEqualTo("abc123");
		assertThat(embedding.getVectorType()).isEqualTo(PostgresMlEmbeddingClient.VectorType.PG_ARRAY);
		assertThat(embedding.getKwargs()).isEqualTo(Map.of("key1", "value1", "key2", "value2"));
		assertThat(embedding.getMetadataMode()).isEqualTo(MetadataMode.ALL);
	}

	@SpringBootConfiguration
	@EnableConfigurationProperties(PostgresMlProperties.class)
	static class TestConfiguration {

	}

}
