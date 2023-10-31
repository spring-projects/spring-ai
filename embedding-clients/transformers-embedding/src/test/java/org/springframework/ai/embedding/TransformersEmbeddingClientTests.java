/*
 * Copyright 2023-2023 the original author or authors.
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

package org.springframework.ai.embedding;

import java.io.File;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import org.springframework.ai.document.Document;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class TransformersEmbeddingClientTests {

	@Test
	void embed() throws Exception {
		TransformersEmbeddingClient embeddingClient = new TransformersEmbeddingClient();
		embeddingClient.afterPropertiesSet();
		List<Double> embed = embeddingClient.embed("Hello world");
		assertThat(embed).hasSize(384);
		assertThat(embed.get(0)).isEqualTo(-0.19744634628295898);
		assertThat(embed.get(383)).isEqualTo(0.17298996448516846);
	}

	@Test
	void embedDocument() throws Exception {
		TransformersEmbeddingClient embeddingClient = new TransformersEmbeddingClient();
		embeddingClient.afterPropertiesSet();
		List<Double> embed = embeddingClient.embed(new Document("Hello world"));
		assertThat(embed).hasSize(384);
		assertThat(embed.get(0)).isEqualTo(-0.19744634628295898);
		assertThat(embed.get(383)).isEqualTo(0.17298996448516846);
	}

	@Test
	void embedList() throws Exception {
		TransformersEmbeddingClient embeddingClient = new TransformersEmbeddingClient();
		embeddingClient.afterPropertiesSet();
		List<List<Double>> embed = embeddingClient.embed(List.of("Hello world", "World is big"));
		assertThat(embed).hasSize(2);
		assertThat(embed.get(0)).hasSize(384);
		assertThat(embed.get(0).get(0)).isEqualTo(-0.19744634628295898);
		assertThat(embed.get(0).get(383)).isEqualTo(0.17298996448516846);

		assertThat(embed.get(1)).hasSize(384);
		assertThat(embed.get(1).get(0)).isEqualTo(0.4293745160102844);
		assertThat(embed.get(1).get(383)).isEqualTo(0.05501303821802139);

		assertThat(embed.get(0)).isNotEqualTo(embed.get(1));
	}

	@Test
	void embedForResponse() throws Exception {
		TransformersEmbeddingClient embeddingClient = new TransformersEmbeddingClient();
		embeddingClient.afterPropertiesSet();
		EmbeddingResponse embed = embeddingClient.embedForResponse(List.of("Hello world", "World is big"));
		assertThat(embed.getData()).hasSize(2);
		assertThat(embed.getMetadata()).isEmpty();

		assertThat(embed.getData().get(0).getEmbedding()).hasSize(384);
		assertThat(embed.getData().get(0).getEmbedding().get(0)).isEqualTo(-0.19744634628295898);
		assertThat(embed.getData().get(0).getEmbedding().get(383)).isEqualTo(0.17298996448516846);

		assertThat(embed.getData().get(1).getEmbedding()).hasSize(384);
		assertThat(embed.getData().get(1).getEmbedding().get(0)).isEqualTo(0.4293745160102844);
		assertThat(embed.getData().get(1).getEmbedding().get(383)).isEqualTo(0.05501303821802139);
	}

	@Test
	void dimensions() throws Exception {

		TransformersEmbeddingClient embeddingClient = new TransformersEmbeddingClient();
		embeddingClient.afterPropertiesSet();
		assertThat(embeddingClient.dimensions()).isEqualTo(384);
		// cached
		assertThat(embeddingClient.dimensions()).isEqualTo(384);
	}

}
