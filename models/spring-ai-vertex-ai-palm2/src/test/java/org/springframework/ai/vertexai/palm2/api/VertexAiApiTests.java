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

package org.springframework.ai.vertexai.palm2.api;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import org.springframework.ai.vertexai.palm2.api.VertexAiApi.Embedding;
import org.springframework.ai.vertexai.palm2.api.VertexAiApi.GenerateMessageRequest;
import org.springframework.ai.vertexai.palm2.api.VertexAiApi.GenerateMessageResponse;
import org.springframework.ai.vertexai.palm2.api.VertexAiApi.MessagePrompt;
import org.springframework.ai.vertexai.palm2.api.VertexAiApi.GenerateMessageResponse.ContentFilter.BlockedReason;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringBootConfiguration;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.context.annotation.Bean;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestToUriTemplate;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * @author Christian Tzolov
 */
@RestClientTest(VertexAiApiTests.Config.class)
public class VertexAiApiTests {

	private final static String TEST_API_KEY = "test-api-key";

	@Autowired
	private VertexAiApi client;

	@Autowired
	private MockRestServiceServer server;

	@Autowired
	private ObjectMapper objectMapper;

	@AfterEach
	void resetMockServer() {
		server.reset();
	}

	@Test
	public void generateMessage() throws JsonProcessingException {

		GenerateMessageRequest request = new GenerateMessageRequest(
				new MessagePrompt(List.of(new VertexAiApi.Message("0", "Hello, how are you?"))));

		GenerateMessageResponse expectedResponse = new GenerateMessageResponse(
				List.of(new VertexAiApi.Message("1", "Hello, how are you?")),
				List.of(new VertexAiApi.Message("0", "I'm fine, thank you.")),
				List.of(new VertexAiApi.GenerateMessageResponse.ContentFilter(BlockedReason.SAFETY, "reason")));

		server
			.expect(requestToUriTemplate("/models/{generative}:generateMessage?key={apiKey}",
					VertexAiApi.DEFAULT_GENERATE_MODEL, TEST_API_KEY))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json(objectMapper.writeValueAsString(request)))
			.andRespond(withSuccess(objectMapper.writeValueAsString(expectedResponse), MediaType.APPLICATION_JSON));

		GenerateMessageResponse response = client.generateMessage(request);

		assertThat(response).isEqualTo(expectedResponse);

		server.verify();
	}

	@Test
	public void embedText() throws JsonProcessingException {

		String text = "Hello, how are you?";

		Embedding expectedEmbedding = new Embedding(List.of(0.1, 0.2, 0.3));

		server
			.expect(requestToUriTemplate("/models/{generative}:embedText?key={apiKey}",
					VertexAiApi.DEFAULT_EMBEDDING_MODEL, TEST_API_KEY))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json(objectMapper.writeValueAsString(Map.of("text", text))))
			.andRespond(withSuccess(objectMapper.writeValueAsString(Map.of("embedding", expectedEmbedding)),
					MediaType.APPLICATION_JSON));

		Embedding embedding = client.embedText(text);

		assertThat(embedding).isEqualTo(expectedEmbedding);

		server.verify();
	}

	@Test
	public void batchEmbedText() throws JsonProcessingException {

		List<String> texts = List.of("Hello, how are you?", "I'm fine, thank you.");

		List<Embedding> expectedEmbeddings = List.of(new Embedding(List.of(0.1, 0.2, 0.3)),
				new Embedding(List.of(0.4, 0.5, 0.6)));

		server
			.expect(requestToUriTemplate("/models/{generative}:batchEmbedText?key={apiKey}",
					VertexAiApi.DEFAULT_EMBEDDING_MODEL, TEST_API_KEY))
			.andExpect(method(HttpMethod.POST))
			.andExpect(content().json(objectMapper.writeValueAsString(Map.of("texts", texts))))
			.andRespond(withSuccess(objectMapper.writeValueAsString(Map.of("embeddings", expectedEmbeddings)),
					MediaType.APPLICATION_JSON));

		List<Embedding> embeddings = client.batchEmbedText(texts);

		assertThat(embeddings).isEqualTo(expectedEmbeddings);

		server.verify();
	}

	@SpringBootConfiguration
	static class Config {

		@Bean
		public VertexAiApi audioApi(RestClient.Builder builder) {
			return new VertexAiApi("", TEST_API_KEY, VertexAiApi.DEFAULT_GENERATE_MODEL,
					VertexAiApi.DEFAULT_EMBEDDING_MODEL, builder);
		}

	}

}
