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

package org.springframework.ai.openai.api;

import org.junit.jupiter.api.Test;
import org.springframework.ai.openai.api.OpenAiApi.Response;
import org.springframework.ai.openai.api.OpenAiApi.ResponseRequest;
import org.springframework.ai.openai.api.OpenAiApi.ResponseStreamEvent;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the Responses API DTOs and methods.
 *
 * @author Alexandros Pappas
 */
class ResponsesApiTest {

	@Test
	void testResponseRequestCreation() {
		ResponseRequest request = new ResponseRequest("Test input", "gpt-4o");

		assertThat(request).isNotNull();
		assertThat(request.input()).isEqualTo("Test input");
		assertThat(request.model()).isEqualTo("gpt-4o");
		assertThat(request.stream()).isFalse();
	}

	@Test
	void testResponseRequestCreationWithStream() {
		ResponseRequest request = new ResponseRequest("Test input", "gpt-4o", true);

		assertThat(request).isNotNull();
		assertThat(request.input()).isEqualTo("Test input");
		assertThat(request.model()).isEqualTo("gpt-4o");
		assertThat(request.stream()).isTrue();
	}

	@Test
	void testResponseRequestWithAllParameters() {
		ResponseRequest request = new ResponseRequest("gpt-4o", "Test input", "You are a helpful assistant", 1000, null,
				0.7, null, null, null, null, false, true, null, null, null, null, null, null, null, null, null, null,
				null, null);

		assertThat(request).isNotNull();
		assertThat(request.model()).isEqualTo("gpt-4o");
		assertThat(request.input()).isEqualTo("Test input");
		assertThat(request.instructions()).isEqualTo("You are a helpful assistant");
		assertThat(request.maxOutputTokens()).isEqualTo(1000);
		assertThat(request.temperature()).isEqualTo(0.7);
		assertThat(request.stream()).isFalse();
		assertThat(request.store()).isTrue();
	}

	@Test
	void testResponseStructure() {
		Response response = new Response("resp_123", "response", 1234567890L, "completed", "gpt-4o", null, null, null,
				null, null, null, null, null, null, null, null, null, null, null, null, null, null, null, null);

		assertThat(response).isNotNull();
		assertThat(response.id()).isEqualTo("resp_123");
		assertThat(response.object()).isEqualTo("response");
		assertThat(response.status()).isEqualTo("completed");
		assertThat(response.model()).isEqualTo("gpt-4o");
	}

	@Test
	void testResponseStreamEventStructure() {
		ResponseStreamEvent event = new ResponseStreamEvent("response.created", 1, null, null, null, null, null, null,
				null, null);

		assertThat(event).isNotNull();
		assertThat(event.type()).isEqualTo("response.created");
		assertThat(event.sequenceNumber()).isEqualTo(1);
	}

}
