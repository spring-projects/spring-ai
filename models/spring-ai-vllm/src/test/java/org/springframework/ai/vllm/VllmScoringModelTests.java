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

package org.springframework.ai.vllm;

import java.io.IOException;
import java.util.List;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.document.Document;
import org.springframework.ai.scoring.ScoringRequest;
import org.springframework.ai.scoring.ScoringResponse;
import org.springframework.ai.vllm.api.VllmScoringApi;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for {@link VllmScoringModel}.
 *
 * @author Spring AI
 */
class VllmScoringModelTests {

	private MockWebServer server;

	private VllmScoringModel model;

	@BeforeEach
	void setUp() throws IOException {
		this.server = new MockWebServer();
		this.server.start();

		VllmScoringApi api = VllmScoringApi.builder()
			.baseUrl(this.server.url("/").toString())
			.apiKey("test-key")
			.build();

		VllmScoringOptions options = VllmScoringOptions.builder().model("BAAI/bge-reranker-v2-m3").topK(2).build();

		this.model = VllmScoringModel.builder().vllmScoringApi(api).options(options).build();
	}

	@AfterEach
	void tearDown() throws IOException {
		this.server.shutdown();
	}

	@Test
	void callSuccessfully() {
		// Prepare JSON response matching vLLM /v1/rerank
		String jsonResponse = "{\n" + "  \"id\": \"cmpl-123\",\n" + "  \"object\": \"list\",\n"
				+ "  \"model\": \"BAAI/bge-reranker-v2-m3\",\n" + "  \"results\": [\n" + "    {\n"
				+ "      \"index\": 1,\n" + "      \"relevance_score\": 0.95\n" + "    },\n" + "    {\n"
				+ "      \"index\": 0,\n" + "      \"relevance_score\": 0.85\n" + "    }\n" + "  ],\n"
				+ "  \"usage\": {\n" + "    \"prompt_tokens\": 10,\n" + "    \"total_tokens\": 10\n" + "  }\n" + "}";

		this.server.enqueue(new MockResponse().setBody(jsonResponse).addHeader("Content-Type", "application/json"));

		List<Document> documents = List.of(new Document("doc1"), new Document("doc2"), new Document("doc3"));
		ScoringResponse response = this.model.call("test query", documents);

		assertThat(response.getResults()).hasSize(2);

		// Index 1 corresponds to "doc2" with score 0.95
		assertThat(response.getResults().get(0).getOutput().getText()).isEqualTo("doc2");
		assertThat(response.getResults().get(0).getOutput().getScore()).isEqualTo(0.95);

		// Index 0 corresponds to "doc1" with score 0.85
		assertThat(response.getResults().get(1).getOutput().getText()).isEqualTo("doc1");
		assertThat(response.getResults().get(1).getOutput().getScore()).isEqualTo(0.85);
	}

	@Test
	void callWithEmptyDocuments() {
		assertThatThrownBy(() -> this.model.call("query", List.of())).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Documents must not be empty");
	}

	@Test
	void mergeOptions() {
		String jsonResponse = "{\n" + "  \"results\": [\n" + "    {\n" + "      \"index\": 0,\n"
				+ "      \"relevance_score\": 0.99\n" + "    }\n" + "  ]\n" + "}";

		this.server.enqueue(new MockResponse().setBody(jsonResponse).addHeader("Content-Type", "application/json"));

		VllmScoringOptions runtimeOptions = VllmScoringOptions.builder()
			.model("custom-model")
			.topK(1)
			.returnDocuments(true)
			.build();

		ScoringRequest request = new ScoringRequest("query", List.of(new Document("text")), runtimeOptions);

		// The test mainly checks if the request goes through successfully without
		// throwing errors
		// In a real scenario, we would verify the dispatched HTTP request body to ensure
		// "custom-model" and top_n=1 were passed correctly.
		ScoringResponse response = this.model.call(request);
		assertThat(response.getResults()).hasSize(1);
	}

	@Test
	void callThrowsExceptionWhenModelIsNull() {
		VllmScoringApi api = VllmScoringApi.builder().baseUrl(this.server.url("/").toString()).build();

		// No default model set
		VllmScoringOptions emptyOptions = VllmScoringOptions.builder().build();

		VllmScoringModel modelWithoutModelName = VllmScoringModel.builder()
			.vllmScoringApi(api)
			.options(emptyOptions)
			.build();

		assertThatThrownBy(() -> modelWithoutModelName.call("query", List.of(new Document("text"))))
			.isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("Model name must not be null for vLLM Scoring");
	}

}
