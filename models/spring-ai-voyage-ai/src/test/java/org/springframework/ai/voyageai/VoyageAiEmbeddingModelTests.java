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

package org.springframework.ai.voyageai;

import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.voyageai.api.VoyageAiApi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;

class VoyageAiEmbeddingModelTests {

	private final MockVoyageAi voyage = new MockVoyageAi();

	private VoyageAiEmbeddingModel model(VoyageAiEmbeddingOptions options) {
		return VoyageAiEmbeddingModel.builder()
			.voyageAiApi(this.voyage.api)
			.options(options)
			.retryTemplate(RetryUtils.SHORT_RETRY_TEMPLATE)
			.build();
	}

	@Test
	void failsWithoutAModelAndSendsNothing() {
		var model = model(VoyageAiEmbeddingOptions.builder().build());
		assertThatIllegalArgumentException().isThrownBy(() -> model.embed("text"))
			.withMessageContaining("no default model");
		this.voyage.server.verify();
	}

	@Test
	void failsForAModelThatIsNotATextEmbeddingModel() {
		var model = model(VoyageAiEmbeddingOptions.builder().model("voyage-multimodal-3.5").build());
		assertThatIllegalArgumentException().isThrownBy(() -> model.embed("text"))
			.withMessageContaining("is not a Voyage AI text embedding model");
		var other = model(VoyageAiEmbeddingOptions.builder().model("text-embedding-3-small").build());
		assertThatIllegalArgumentException().isThrownBy(() -> other.embed("text"))
			.withMessageContaining("is not a Voyage AI text embedding model");
	}

	@Test
	void requestOptionsOverrideTheDefaultsAndUnsetOptionsAreNotSent() {
		this.voyage.expectPost("/v1/embeddings")
			.andExpect(jsonPath("$.model").value("voyage-4-lite"))
			.andExpect(jsonPath("$.input_type").value("query"))
			.andExpect(jsonPath("$.output_dimension").value(256))
			.andExpect(jsonPath("$.truncation").doesNotExist())
			.andRespond(MockVoyageAi.embeddings(1, 0, MockVoyageAi.USAGE));
		this.voyage.expectPost("/v1/embeddings")
			.andExpect(jsonPath("$.model").value("voyage-4"))
			.andExpect(jsonPath("$.input_type").doesNotExist())
			.andExpect(jsonPath("$.output_dimension").doesNotExist())
			.andRespond(MockVoyageAi.embeddings(1, 0, MockVoyageAi.USAGE));

		var model = model(VoyageAiEmbeddingOptions.builder().model("voyage-4").build());
		model.call(new EmbeddingRequest(List.of("a"),
				VoyageAiEmbeddingOptions.builder()
					.model("voyage-4-lite")
					.inputType(VoyageAiApi.InputType.QUERY)
					.dimensions(256)
					.build()));
		model.call(new EmbeddingRequest(List.of("a"), EmbeddingOptions.builder().build()));
		this.voyage.server.verify();
	}

	@Test
	void splitsMoreThanAThousandInputsAndKeepsTheirOrder() {
		this.voyage.expectPost("/v1/embeddings")
			.andExpect(jsonPath("$.input.length()").value(1000))
			.andExpect(jsonPath("$.input[0]").value("t0"))
			.andRespond(MockVoyageAi.embeddings(1000, 0, MockVoyageAi.USAGE));
		this.voyage.expectPost("/v1/embeddings")
			.andExpect(jsonPath("$.input.length()").value(1000))
			.andExpect(jsonPath("$.input[0]").value("t1000"))
			.andRespond(MockVoyageAi.embeddings(1000, 1000, MockVoyageAi.USAGE));
		this.voyage.expectPost("/v1/embeddings")
			.andExpect(jsonPath("$.input.length()").value(500))
			.andExpect(jsonPath("$.input[0]").value("t2000"))
			.andRespond(MockVoyageAi.embeddings(500, 2000, MockVoyageAi.USAGE));

		List<String> texts = java.util.stream.IntStream.range(0, 2500).mapToObj(i -> "t" + i).toList();
		EmbeddingResponse response = model(VoyageAiEmbeddingOptions.builder().model("voyage-4").build())
			.call(new EmbeddingRequest(texts, EmbeddingOptions.builder().build()));

		assertThat(response.getResults()).hasSize(2500);
		for (int i = 0; i < 2500; i++) {
			assertThat(response.getResults().get(i).getOutput()).containsExactly((float) i);
			assertThat(response.getResults().get(i).getIndex()).isEqualTo(i);
		}
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(30);
		this.voyage.server.verify();
	}

	@Test
	void failsClosedWhenTheResponseHasNoUsage() {
		this.voyage.expectPost("/v1/embeddings").andRespond(MockVoyageAi.embeddings(1, 0, ""));
		var model = model(VoyageAiEmbeddingOptions.builder().model("voyage-4").build());
		assertThatIllegalStateException().isThrownBy(() -> model.embed("text"))
			.withMessageContaining("no usage.total_tokens");
	}

	@Test
	void failsWhenTheNumberOfEmbeddingsDiffersFromTheNumberOfInputs() {
		this.voyage.expectPost("/v1/embeddings").andRespond(MockVoyageAi.embeddings(1, 0, MockVoyageAi.USAGE));
		var model = model(VoyageAiEmbeddingOptions.builder().model("voyage-4").build());
		assertThatIllegalStateException().isThrownBy(() -> model.embed(List.of("a", "b")))
			.withMessageContaining("1 embeddings for 2 inputs");
	}

	@Test
	void dimensionsFollowTheOptionsAndNeedAModel() {
		assertThat(model(VoyageAiEmbeddingOptions.builder().model("voyage-4").build()).dimensions()).isEqualTo(1024);
		assertThat(model(VoyageAiEmbeddingOptions.builder().model("voyage-4").dimensions(2048).build()).dimensions())
			.isEqualTo(2048);
		assertThatIllegalArgumentException()
			.isThrownBy(() -> model(VoyageAiEmbeddingOptions.builder().build()).dimensions());
	}

	@Test
	void embeddingNoTextsSendsNothing() {
		var model = model(VoyageAiEmbeddingOptions.builder().model("voyage-4").build());
		assertThat(model.embed(Collections.<String>emptyList())).isEmpty();
		this.voyage.server.verify();
	}

}
