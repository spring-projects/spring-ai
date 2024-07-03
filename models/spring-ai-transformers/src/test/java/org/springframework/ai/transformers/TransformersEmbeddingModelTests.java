/*
 * Copyright 2023 - 2024 the original author or authors.
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
package org.springframework.ai.transformers;

import java.text.DecimalFormat;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.embedding.EmbeddingResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class TransformersEmbeddingModelTests {

	private static DecimalFormat DF = new DecimalFormat("#.#####");

	@Test
	void embed() throws Exception {

		TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel();
		embeddingModel.afterPropertiesSet();
		List<Double> embed = embeddingModel.embed("Hello world");
		assertThat(embed).hasSize(384);
		assertThat(DF.format(embed.get(0))).isEqualTo(DF.format(-0.19744634628295898));
		assertThat(DF.format(embed.get(383))).isEqualTo(DF.format(0.17298996448516846));
	}

	@Test
	void embedDocument() throws Exception {
		TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel();
		embeddingModel.afterPropertiesSet();
		List<Double> embed = embeddingModel.embed(new Document("Hello world"));
		assertThat(embed).hasSize(384);
		assertThat(DF.format(embed.get(0))).isEqualTo(DF.format(-0.19744634628295898));
		assertThat(DF.format(embed.get(383))).isEqualTo(DF.format(0.17298996448516846));
	}

	@Test
	void embedList() throws Exception {
		TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel();
		embeddingModel.afterPropertiesSet();
		List<List<Double>> embed = embeddingModel.embed(List.of("Hello world", "World is big"));
		assertThat(embed).hasSize(2);
		assertThat(embed.get(0)).hasSize(384);
		assertThat(DF.format(embed.get(0).get(0))).isEqualTo(DF.format(-0.19744634628295898));
		assertThat(DF.format(embed.get(0).get(383))).isEqualTo(DF.format(0.17298996448516846));

		assertThat(embed.get(1)).hasSize(384);
		assertThat(DF.format(embed.get(1).get(0))).isEqualTo(DF.format(0.4293745160102844));
		assertThat(DF.format(embed.get(1).get(383))).isEqualTo(DF.format(0.05501303821802139));

		assertThat(embed.get(0)).isNotEqualTo(embed.get(1));
	}

	@Test
	void embedForResponse() throws Exception {
		TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel();
		embeddingModel.afterPropertiesSet();
		EmbeddingResponse embed = embeddingModel.embedForResponse(List.of("Hello world", "World is big"));
		assertThat(embed.getResults()).hasSize(2);
		assertThat(embed.getMetadata()).isEmpty();

		assertThat(embed.getResults().get(0).getOutput()).hasSize(384);
		assertThat(DF.format(embed.getResults().get(0).getOutput().get(0))).isEqualTo(DF.format(-0.19744634628295898));
		assertThat(DF.format(embed.getResults().get(0).getOutput().get(383))).isEqualTo(DF.format(0.17298996448516846));

		assertThat(embed.getResults().get(1).getOutput()).hasSize(384);
		assertThat(DF.format(embed.getResults().get(1).getOutput().get(0))).isEqualTo(DF.format(0.4293745160102844));
		assertThat(DF.format(embed.getResults().get(1).getOutput().get(383))).isEqualTo(DF.format(0.05501303821802139));
	}

	@Test
	void dimensions() throws Exception {

		TransformersEmbeddingModel embeddingModel = new TransformersEmbeddingModel();
		embeddingModel.afterPropertiesSet();
		assertThat(embeddingModel.dimensions()).isEqualTo(384);
		// cached
		assertThat(embeddingModel.dimensions()).isEqualTo(384);
	}

}
