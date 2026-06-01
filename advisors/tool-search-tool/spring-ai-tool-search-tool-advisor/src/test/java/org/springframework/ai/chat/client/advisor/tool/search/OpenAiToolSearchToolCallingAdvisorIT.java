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

package org.springframework.ai.chat.client.advisor.tool.search;

import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

/**
 * OpenAI-backed integration tests for {@link ToolSearchToolCallingAdvisor}.
 *
 * <p>
 * Requires the {@code OPENAI_API_KEY} environment variable to be set.
 *
 * @author Christian Tzolov
 */
@EnabledIfEnvironmentVariable(named = "OPENAI_API_KEY", matches = ".+")
class OpenAiToolSearchToolCallingAdvisorIT extends AbstractToolSearchToolCallingAdvisorIT {

	@Override
	protected ChatModel getChatModel() {
		return OpenAiChatModel.builder()
			.options(OpenAiChatOptions.builder()
				.apiKey(System.getenv("OPENAI_API_KEY"))
				.model(OpenAiChatOptions.DEFAULT_CHAT_MODEL)
				.build())
			.build();
	}

}
