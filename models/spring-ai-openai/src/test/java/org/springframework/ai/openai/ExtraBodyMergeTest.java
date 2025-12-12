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

package org.springframework.ai.openai;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionMessage;
import org.springframework.ai.openai.api.OpenAiApi.ChatCompletionRequest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests to verify that extraBody is preserved when using ModelOptionsUtils.merge().
 *
 * @author senrey_song
 */
class ExtraBodyMergeTest {

	@Test
	void shouldPreserveExtraBodyAfterMerge() {
		List<ChatCompletionMessage> messages = List
			.of(new ChatCompletionMessage("test message", OpenAiApi.ChatCompletionMessage.Role.USER));
		ChatCompletionRequest request = new ChatCompletionRequest(messages, false);

		OpenAiChatOptions requestOptions = OpenAiChatOptions.builder()
			.extraBody(Map.of("top_k", 50, "repetition_penalty", 1.1, "custom_param", "custom_value"))
			.build();

		request = ModelOptionsUtils.merge(requestOptions, request, ChatCompletionRequest.class,
				ChatCompletionRequest.getMergableFieldNames());

		assertThat(request.extraBody()).isNotNull();
		@SuppressWarnings("unchecked")
		Map<String, Object> extraBodyMap = (Map<String, Object>) request.extraBody().get("extra_body");
		assertThat(extraBodyMap).hasSize(3)
			.containsEntry("top_k", 50)
			.containsEntry("repetition_penalty", 1.1)
			.containsEntry("custom_param", "custom_value");
	}

}
