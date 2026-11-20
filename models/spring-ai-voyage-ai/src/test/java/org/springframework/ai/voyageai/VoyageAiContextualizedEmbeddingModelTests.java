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

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.embedding.EmbeddingOptions;
import org.springframework.ai.embedding.EmbeddingRequest;
import org.springframework.ai.embedding.EmbeddingResponse;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.voyageai.api.VoyageAiApi;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VoyageAiContextualizedEmbeddingModelTests {

	private final MockVoyageAi voyage = new MockVoyageAi();

	private VoyageAiContextualizedEmbeddingModel model(VoyageAiContextualizedEmbeddingOptions options) {
		return VoyageAiContextualizedEmbeddingModel.builder()
			.voyageAiApi(this.voyage.api)
			.options(options)
			.retryTemplate(RetryUtils.SHORT_RETRY_TEMPLATE)
			.build();
	}

	private static VoyageAiContextualizedEmbeddingOptions options(VoyageAiApi.InputType inputType) {
		return VoyageAiContextualizedEmbeddingOptions.builder().model("voyage-context-4").inputType(inputType).build();
	}

	@Test
	void documentsAreSentAsOneStringEachWithAutoChunking() {
		this.voyage.expectPost("/v1/contextualizedembeddings")
			.andExpect(jsonPath("$.inputs[0]").value("first document"))
			.andExpect(jsonPath("$.inputs[1]").value("second document"))
			.andExpect(jsonPath("$.input_type").value("document"))
			.andExpect(jsonPath("$.enable_auto_chunking").value(true))
			.andExpect(jsonPath("$.chunk_size").value(32000))
			.andExpect(jsonPath("$.output_dimension").value(256))
			.andRespond(withSuccess(
					"""
							{"object":"list","model":"voyage-context-4","usage":{"total_tokens":9},"data":[
							{"object":"list","index":1,"data":[{"object":"embedding","embedding":[3.0],"index":0,"text":"second document"}]},
							{"object":"list","index":0,"data":[
							{"object":"embedding","embedding":[2.0],"index":1,"text":"chunk b"},
							{"object":"embedding","embedding":[1.0],"index":0,"text":"chunk a"}]}]}
							""",
					MediaType.APPLICATION_JSON));

		EmbeddingResponse response = model(options(VoyageAiApi.InputType.DOCUMENT))
			.call(new EmbeddingRequest(List.of("first document", "second document"),
					VoyageAiContextualizedEmbeddingOptions.builder()
						.model("voyage-context-4")
						.inputType(VoyageAiApi.InputType.DOCUMENT)
						.dimensions(256)
						.build()));

		assertThat(response.getResults()).hasSize(3);
		assertThat(response.getResults().get(0).getOutput()).containsExactly(1.0f);
		assertThat(response.getResults().get(0).getMetadata().getDocumentId()).isEqualTo("0");
		assertThat(response.getResults().get(0).getMetadata().getDocumentData()).isEqualTo("chunk a");
		assertThat(response.getResults().get(1).getMetadata().getDocumentData()).isEqualTo("chunk b");
		assertThat(response.getResults().get(2).getMetadata().getDocumentId()).isEqualTo("1");
		assertThat(response.getResults().get(2).getMetadata().getDocumentData()).isEqualTo("second document");
		assertThat(response.getMetadata().getUsage().getTotalTokens()).isEqualTo(9);
		this.voyage.server.verify();
	}

	@Test
	void queriesAreSentWithoutAutoChunking() {
		this.voyage.expectPost("/v1/contextualizedembeddings")
			.andExpect(jsonPath("$.inputs[0][0]").value("a query"))
			.andExpect(jsonPath("$.inputs[0].length()").value(1))
			.andExpect(jsonPath("$.input_type").value("query"))
			.andExpect(jsonPath("$.enable_auto_chunking").doesNotExist())
			.andExpect(jsonPath("$.chunk_size").doesNotExist())
			.andRespond(withSuccess("""
					{"object":"list","model":"voyage-context-4","usage":{"total_tokens":3},"data":[
					{"object":"list","index":0,"data":[{"object":"embedding","embedding":[1.0],"index":0}]}]}
					""", MediaType.APPLICATION_JSON));

		EmbeddingResponse response = model(options(VoyageAiApi.InputType.QUERY))
			.call(new EmbeddingRequest(List.of("a query"), EmbeddingOptions.builder().build()));

		assertThat(response.getResults()).hasSize(1);
		assertThat(response.getResults().get(0).getMetadata().getDocumentData()).isNull();
		this.voyage.server.verify();
	}

	@Test
	void failsWithoutAnInputTypeAndSendsNothing() {
		var model = model(VoyageAiContextualizedEmbeddingOptions.builder().model("voyage-context-4").build());
		assertThatIllegalArgumentException()
			.isThrownBy(() -> model.call(new EmbeddingRequest(List.of("doc"), EmbeddingOptions.builder().build())))
			.withMessageContaining("input type must be set");
		this.voyage.server.verify();
	}

	@Test
	void failsWithoutAModelOrWithAModelOfAnotherEndpoint() {
		var none = model(
				VoyageAiContextualizedEmbeddingOptions.builder().inputType(VoyageAiApi.InputType.DOCUMENT).build());
		assertThatIllegalArgumentException()
			.isThrownBy(() -> none.call(new EmbeddingRequest(List.of("doc"), EmbeddingOptions.builder().build())))
			.withMessageContaining("no default model");
		var other = model(VoyageAiContextualizedEmbeddingOptions.builder()
			.model("voyage-4")
			.inputType(VoyageAiApi.InputType.DOCUMENT)
			.build());
		assertThatIllegalArgumentException()
			.isThrownBy(() -> other.call(new EmbeddingRequest(List.of("doc"), EmbeddingOptions.builder().build())))
			.withMessageContaining("is not a Voyage AI contextualized embedding model");
	}

	@Test
	void failsClosedWhenTheResponseHasNoUsage() {
		this.voyage.expectPost("/v1/contextualizedembeddings").andRespond(withSuccess("""
				{"object":"list","model":"voyage-context-4","data":[
				{"object":"list","index":0,"data":[{"object":"embedding","embedding":[1.0],"index":0,"text":"doc"}]}]}
				""", MediaType.APPLICATION_JSON));
		var model = model(options(VoyageAiApi.InputType.DOCUMENT));
		org.assertj.core.api.Assertions.assertThatIllegalStateException()
			.isThrownBy(() -> model.call(new EmbeddingRequest(List.of("doc"), EmbeddingOptions.builder().build())))
			.withMessageContaining("no usage.total_tokens");
	}

}
