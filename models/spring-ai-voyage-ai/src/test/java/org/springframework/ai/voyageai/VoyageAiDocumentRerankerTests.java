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

import org.springframework.ai.document.Document;
import org.springframework.ai.rag.Query;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatIllegalStateException;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class VoyageAiDocumentRerankerTests {

	private final MockVoyageAi voyage = new MockVoyageAi();

	private VoyageAiDocumentReranker.Builder reranker() {
		return VoyageAiDocumentReranker.builder()
			.voyageAiApi(this.voyage.api)
			.retryTemplate(RetryUtils.SHORT_RETRY_TEMPLATE);
	}

	@Test
	void reordersByRelevanceKeepsTheTopKAndSetsTheScore() {
		this.voyage.expectPost("/v1/rerank")
			.andExpect(jsonPath("$.query").value("capital of France"))
			.andExpect(jsonPath("$.model").value("rerank-3"))
			.andExpect(jsonPath("$.top_k").value(2))
			.andExpect(jsonPath("$.return_documents").value(true))
			.andExpect(jsonPath("$.documents.length()").value(3))
			.andRespond(withSuccess("""
					{"object":"list","model":"rerank-3","usage":{"total_tokens":30},"data":[
					{"index":2,"relevance_score":0.9,"document":"c"},{"index":0,"relevance_score":0.4,"document":"a"}]}
					""", MediaType.APPLICATION_JSON));

		var documents = List.of(new Document("a"), new Document("b"), new Document("c"));
		List<Document> ranked = reranker().model("rerank-3")
			.topK(2)
			.build()
			.process(new Query("capital of France"), documents);

		assertThat(ranked).extracting(Document::getText).containsExactly("c", "a");
		assertThat(ranked).extracting(Document::getScore).containsExactly(0.9, 0.4);
		assertThat(ranked.get(0).getId()).isEqualTo(documents.get(2).getId());
		this.voyage.server.verify();
	}

	@Test
	void theModelIsRequiredAndMustBeARerankModel() {
		assertThatIllegalArgumentException().isThrownBy(() -> reranker().build())
			.withMessageContaining("no default model");
		assertThatIllegalArgumentException().isThrownBy(() -> reranker().model("voyage-4").build())
			.withMessageContaining("is not a Voyage AI rerank model");
	}

	@Test
	void rejectsMoreThanAThousandDocumentsInsteadOfCuttingThemOff() {
		List<Document> documents = Collections.nCopies(1001, new Document("a"));
		assertThatIllegalArgumentException()
			.isThrownBy(() -> reranker().model("rerank-3").build().process(new Query("q"), documents))
			.withMessageContaining("at most 1000");
		this.voyage.server.verify();
	}

	@Test
	void rejectsADocumentWithoutText() {
		var noText = new Document(org.springframework.ai.content.Media.builder()
			.mimeType(org.springframework.util.MimeTypeUtils.IMAGE_PNG)
			.data(new byte[] { 1 })
			.build(), java.util.Map.of());
		assertThatIllegalArgumentException()
			.isThrownBy(() -> reranker().model("rerank-3").build().process(new Query("q"), List.of(noText)))
			.withMessageContaining("has no text to rerank");
	}

	@Test
	void failsClosedWhenTheResponseHasNoUsage() {
		this.voyage.expectPost("/v1/rerank").andRespond(withSuccess("""
				{"object":"list","model":"rerank-3","data":[{"index":0,"relevance_score":0.4}]}
				""", MediaType.APPLICATION_JSON));
		assertThatIllegalStateException()
			.isThrownBy(() -> reranker().model("rerank-3").build().process(new Query("q"), List.of(new Document("a"))))
			.withMessageContaining("no usage.total_tokens");
	}

	@Test
	void failsWhenVoyageReturnsAnIndexOutsideTheDocuments() {
		this.voyage.expectPost("/v1/rerank")
			.andRespond(withSuccess(
					"""
							{"object":"list","model":"rerank-3","usage":{"total_tokens":1},"data":[{"index":5,"relevance_score":0.4}]}
							""",
					MediaType.APPLICATION_JSON));
		assertThatIllegalStateException()
			.isThrownBy(() -> reranker().model("rerank-3").build().process(new Query("q"), List.of(new Document("a"))))
			.withMessageContaining("document index 5");
	}

	@Test
	void noDocumentsNeedNoRequest() {
		assertThat(reranker().model("rerank-3").build().process(new Query("q"), List.of())).isEmpty();
		this.voyage.server.verify();
	}

}
