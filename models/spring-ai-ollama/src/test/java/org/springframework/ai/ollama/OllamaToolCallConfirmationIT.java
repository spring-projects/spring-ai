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

package org.springframework.ai.ollama;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.ollama.api.OllamaApi;
import org.springframework.ai.ollama.api.OllamaChatOptions;
import org.springframework.ai.ollama.api.OllamaModel;
import org.springframework.ai.retry.RetryUtils;
import org.springframework.ai.test.chat.client.advisor.AbstractToolCallConfirmationIT;

/**
 * Tool call confirmation with Ollama. Ollama runs in a container, or locally with
 * {@code OLLAMA_WITH_REUSE=true}, as for the other Ollama integration tests.
 *
 * @author Christian Tzolov
 */
class OllamaToolCallConfirmationIT extends AbstractToolCallConfirmationIT {

	private static final String MODEL = OllamaModel.QWEN_2_5_3B.getName();

	private static OllamaApi ollamaApi;

	@BeforeAll
	static void startOllama() {
		ollamaApi = BaseOllamaIT.initializeOllama(MODEL);
	}

	@AfterAll
	static void stopOllama() {
		BaseOllamaIT.tearDown();
	}

	@Override
	protected ChatModel getChatModel() {
		return OllamaChatModel.builder()
			.ollamaApi(ollamaApi)
			.options(OllamaChatOptions.builder().model(MODEL).temperature(0.0).build())
			.retryTemplate(RetryUtils.DEFAULT_RETRY_TEMPLATE)
			.build();
	}

}
