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

import java.util.Map;

import org.junit.jupiter.api.Test;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.SimpleApiKey;
import org.springframework.ai.openai.api.OpenAiApi;
import org.springframework.ai.tool.ToolCallback;
import org.springframework.ai.tool.function.FunctionToolCallback;

import static org.assertj.core.api.Assertions.assertThat;

class OpenAiChatModelTest {

	@Test
	void testCreateRequest() {
		ToolCallback toolCallback = FunctionToolCallback.builder("sayHello", str -> "Hello " + str)
			.description("Say hello")
			.inputType(String.class)
			.build();

		OpenAiChatOptions runtimeOptions = OpenAiChatOptions.builder()
			.internalToolExecutionEnabled(false)
			.toolCallbacks(toolCallback)
			.extraBody(Map.of("chat_template_kwargs", Map.of("enable_thinking", false)))
			.build();
		OpenAiChatModel chatModel = OpenAiChatModel.builder()
			.openAiApi(OpenAiApi.builder().apiKey(new SimpleApiKey("TEST")).build())
			.build();
		Prompt prompt = new Prompt("Test message content", runtimeOptions);
		OpenAiApi.ChatCompletionRequest request = chatModel.createRequest(prompt, false);

		assertThat(request.extraBody()).isNotNull();
		assertThat(request.extraBody()).isNotEmpty();
		assertThat(request.extraBody().size()).isEqualTo(1);
		assertThat(request.extraBody().containsKey("chat_template_kwargs")).isTrue();
		assertThat(((Map<String, Object>) request.extraBody().get("chat_template_kwargs"))).isNotNull();
		assertThat(
				((Map<String, Object>) request.extraBody().get("chat_template_kwargs")).containsKey("enable_thinking"))
			.isTrue();
		assertThat(((Map<String, Object>) request.extraBody().get("chat_template_kwargs")).get("enable_thinking"))
			.isEqualTo(false);

	}

}
