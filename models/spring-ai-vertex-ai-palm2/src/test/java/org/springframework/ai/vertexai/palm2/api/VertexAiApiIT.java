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
import java.util.stream.Collectors;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.vertexai.palm2.api.VertexAiApi.Embedding;
import org.springframework.ai.vertexai.palm2.api.VertexAiApi.GenerateMessageRequest;
import org.springframework.ai.vertexai.palm2.api.VertexAiApi.GenerateMessageResponse;
import org.springframework.ai.vertexai.palm2.api.VertexAiApi.MessagePrompt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link VertexAiApi}. Requires a valid API key to be set via the
 * {@code PALM_API_KEY} environment and at the moment Google enables is it only in the US
 * region (so use VPN for testing).
 *
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "PALM_API_KEY", matches = ".*")
public class VertexAiApiIT {

	VertexAiApi vertexAiApi = new VertexAiApi(System.getenv("PALM_API_KEY"));

	@Test
	public void generateMessage() {

		var prompt = new MessagePrompt(List.of(new VertexAiApi.Message("0", "Hello, how are you?")));

		GenerateMessageRequest request = new GenerateMessageRequest(prompt);

		GenerateMessageResponse response = vertexAiApi.generateMessage(request);

		assertThat(response).isNotNull();

		// Vertex returns the prompt messages in the response's messages list.
		assertThat(response.messages()).hasSize(1);
		assertThat(response.messages().get(0)).isEqualTo(prompt.messages().get(0));

		// Vertex returns the answer in the response's candidates list.
		assertThat(response.candidates()).hasSize(1);
		assertThat(response.candidates().get(0).author()).isNotBlank();
		assertThat(response.candidates().get(0).content()).isNotBlank();
	}

	@Test
	public void embedText() {

		var text = "Hello, how are you?";

		Embedding response = vertexAiApi.embedText(text);

		assertThat(response).isNotNull();
		assertThat(response.value()).hasSize(768);
	}

	@Test
	public void batchEmbedText() {

		var text = List.of("Hello, how are you?", "I am fine, thank you!");

		List<Embedding> response = vertexAiApi.batchEmbedText(text);

		assertThat(response).isNotNull();
		assertThat(response).hasSize(2);
		assertThat(response.get(0).value()).hasSize(768);
		assertThat(response.get(1).value()).hasSize(768);
	}

	@Test
	public void countMessageTokens() {

		var text = "Hello, how are you?";

		var prompt = new MessagePrompt(List.of(new VertexAiApi.Message("0", text)));
		int response = vertexAiApi.countMessageTokens(prompt);

		assertThat(response).isEqualTo(17);
	}

	@Test
	public void listModels() {

		List<String> response = vertexAiApi.listModels();

		assertThat(response).isNotNull();
		assertThat(response).hasSizeGreaterThan(0);
		assertThat(response).contains("models/chat-bison-001", "models/text-bison-001", "models/embedding-gecko-001");

		System.out.println(" - " + response.stream()
			.map(vertexAiApi::getModel)
			.map(VertexAiApi.Model::toString)
			.collect(Collectors.joining("\n - ")));
	}

	@Test
	public void getModel() {

		VertexAiApi.Model model = vertexAiApi.getModel("models/chat-bison-001");

		System.out.println(model);
		assertThat(model).isNotNull();
		assertThat(model.displayName()).isEqualTo("PaLM 2 Chat (Legacy)");
	}

}
