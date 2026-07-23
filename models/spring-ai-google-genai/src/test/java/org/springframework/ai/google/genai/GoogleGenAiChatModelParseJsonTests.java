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

package org.springframework.ai.google.genai;

import java.util.List;
import java.util.Map;

import com.google.genai.types.Part;
import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.messages.ToolResponseMessage;
import org.springframework.ai.chat.messages.ToolResponseMessage.ToolResponse;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for
 * {@link GoogleGenAiChatModel#messageToGeminiParts(org.springframework.ai.chat.messages.Message)}
 * verifying that non-JSON tool responses are handled gracefully instead of throwing
 * RuntimeException.
 *
 * @author Jinan Choi
 * @since 2.0.0
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/5437">GH-5437</a>
 */
class GoogleGenAiChatModelParseJsonTests {

	@Test
	void messageToGeminiPartsShouldHandlePlainTextToolResponse() {
		// A tool that returns a datetime string (not valid JSON)
		String plainTextResult = "2026-02-11 00:14:54 (Tuesday) [UTC]";
		ToolResponseMessage message = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("call-1", "current_datetime", plainTextResult)))
			.build();

		List<Part> parts = GoogleGenAiChatModel.messageToGeminiParts(message);

		assertThat(parts).hasSize(1);
		Map<String, Object> response = parts.get(0).functionResponse().get().response().get();
		assertThat(response).containsEntry("result", plainTextResult);
	}

	@Test
	void messageToGeminiPartsShouldHandleErrorTextToolResponse() {
		// A tool that returns an error message (not valid JSON)
		String errorResult = "Error: Invalid timezone 'foo/bar'";
		ToolResponseMessage message = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("call-2", "current_datetime", errorResult)))
			.build();

		List<Part> parts = GoogleGenAiChatModel.messageToGeminiParts(message);

		assertThat(parts).hasSize(1);
		Map<String, Object> response = parts.get(0).functionResponse().get().response().get();
		assertThat(response).containsEntry("result", errorResult);
	}

	@Test
	void messageToGeminiPartsShouldHandleValidJsonToolResponse() {
		// A tool that returns valid JSON (should still work as before)
		String jsonResult = "{\"temperature\": 25, \"unit\": \"celsius\"}";
		ToolResponseMessage message = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("call-3", "weather", jsonResult)))
			.build();

		List<Part> parts = GoogleGenAiChatModel.messageToGeminiParts(message);

		assertThat(parts).hasSize(1);
		Map<String, Object> response = parts.get(0).functionResponse().get().response().get();
		assertThat(response).containsEntry("temperature", 25);
		assertThat(response).containsEntry("unit", "celsius");
	}

	@Test
	void messageToGeminiPartsShouldHandleJsonArrayToolResponse() {
		// A tool that returns a JSON array (should be wrapped in {"result": [...]})
		String arrayResult = "[\"item1\", \"item2\", \"item3\"]";
		ToolResponseMessage message = ToolResponseMessage.builder()
			.responses(List.of(new ToolResponse("call-4", "list_items", arrayResult)))
			.build();

		List<Part> parts = GoogleGenAiChatModel.messageToGeminiParts(message);

		assertThat(parts).hasSize(1);
		Map<String, Object> response = parts.get(0).functionResponse().get().response().get();
		assertThat(response).containsKey("result");
		assertThat(response.get("result")).isInstanceOf(List.class);
	}

}
