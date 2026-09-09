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

package org.springframework.ai.openai;

import java.util.Map;

import com.openai.core.JsonValue;
import com.openai.errors.InternalServerException;
import com.openai.errors.RateLimitException;
import com.openai.errors.UnexpectedStatusCodeException;
import com.openai.models.chat.completions.ChatCompletion;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Tests for surfacing error-only responses from OpenAI API compatible providers, such as
 * OpenRouter, as openai-java service exceptions.
 *
 * @see <a href="https://github.com/spring-projects/spring-ai/issues/6900">GH-6900</a>
 */
class OpenAiChatModelErrorResponseTests {

	private static ChatCompletion completionFrom(Map<String, Object> body) {
		return JsonValue.from(body).convert(ChatCompletion.class);
	}

	@Test
	void upstreamServerErrorIsSurfacedAsInternalServerException() {
		ChatCompletion completion = completionFrom(Map.of("id", "gen-x", "error",
				Map.of("message", "Upstream error from Nvidia: Service temporarily overloaded", "code", 502)));

		assertThatThrownBy(() -> OpenAiChatModel.throwIfErrorResponse(completion))
			.isInstanceOf(InternalServerException.class)
			.hasMessageContaining("Upstream error from Nvidia: Service temporarily overloaded");
	}

	@Test
	void rateLimitErrorIsSurfacedAsRateLimitException() {
		ChatCompletion completion = completionFrom(
				Map.of("id", "gen-x", "error", Map.of("message", "Rate limit exceeded", "code", 429)));

		assertThatThrownBy(() -> OpenAiChatModel.throwIfErrorResponse(completion))
			.isInstanceOf(RateLimitException.class)
			.hasMessageContaining("Rate limit exceeded");
	}

	@Test
	void errorWithoutCodeIsSurfacedAsUnexpectedStatusCodeException() {
		ChatCompletion completion = completionFrom(
				Map.of("id", "gen-x", "error", Map.of("message", "Something went wrong")));

		assertThatThrownBy(() -> OpenAiChatModel.throwIfErrorResponse(completion))
			.isInstanceOf(UnexpectedStatusCodeException.class)
			.hasMessageContaining("Something went wrong");
	}

	@Test
	void responseWithChoicesIsNotRejected() {
		ChatCompletion completion = completionFrom(
				Map.of("id", "chatcmpl-1", "created", 1, "model", "gpt-4o", "object", "chat.completion", "choices",
						java.util.List.of(Map.of("index", 0, "finish_reason", "stop", "logprobs",
								new java.util.HashMap<String, Object>(), "message",
								Map.of("role", "assistant", "content", "Hello", "refusal", "")))));

		assertThatCode(() -> OpenAiChatModel.throwIfErrorResponse(completion)).doesNotThrowAnyException();
		assertThat(completion.choices()).hasSize(1);
	}

	@Test
	void responseWithoutChoicesAndWithoutErrorIsNotRejected() {
		ChatCompletion completion = completionFrom(Map.of("id", "gen-x"));

		assertThatCode(() -> OpenAiChatModel.throwIfErrorResponse(completion)).doesNotThrowAnyException();
	}

}
