/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.azure.openai;

import com.azure.ai.openai.OpenAIClient;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import org.springframework.ai.chat.prompt.Prompt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * @author Christian Tzolov
 */
public class AzureChatCompletionsOptionsTests {

	@Test
	public void createRequestWithChatOptions() {

		OpenAIClient mockClient = Mockito.mock(OpenAIClient.class);
		var client = new AzureOpenAiChatClient(mockClient,
				AzureOpenAiChatOptions.builder().withModel("DEFAULT_MODEL").withTemperature(66.6f).build());

		var requestOptions = client.toAzureChatCompletionsOptions(new Prompt("Test message content"));

		assertThat(requestOptions.getMessages()).hasSize(1);

		assertThat(requestOptions.getModel()).isEqualTo("DEFAULT_MODEL");
		assertThat(requestOptions.getTemperature()).isEqualTo(66.6f);

		requestOptions = client.toAzureChatCompletionsOptions(new Prompt("Test message content",
				AzureOpenAiChatOptions.builder().withModel("PROMPT_MODEL").withTemperature(99.9f).build()));

		assertThat(requestOptions.getMessages()).hasSize(1);

		assertThat(requestOptions.getModel()).isEqualTo("PROMPT_MODEL");
		assertThat(requestOptions.getTemperature()).isEqualTo(99.9f);
	}

}
