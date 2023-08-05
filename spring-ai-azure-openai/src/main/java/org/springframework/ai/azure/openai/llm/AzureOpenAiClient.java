/*
 * Copyright 2023 the original author or authors.
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

package org.springframework.ai.azure.openai.llm;

import java.util.List;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.ChatChoice;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.ai.openai.models.ChatMessage;
import com.azure.ai.openai.models.ChatRole;
import com.azure.ai.openai.models.Choice;
import com.azure.ai.openai.models.Completions;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.ai.core.llm.LLMResult;
import org.springframework.ai.core.llm.LlmClient;
import org.springframework.ai.core.prompt.Prompt;
import org.springframework.util.Assert;

/**
 * Implementation of {@link LlmClient} backed by an OpenAiService
 */
public class AzureOpenAiClient implements LlmClient {

	private static final Logger logger = LoggerFactory.getLogger(AzureOpenAiClient.class);

	private final OpenAIClient msoftOpenAiClient;

	private Double temperature = 0.5;

	private String model = "gpt-35-turbo";

	public AzureOpenAiClient(OpenAIClient msoftOpenAiClient) {
		Assert.notNull(msoftOpenAiClient, "OpenAiClient must not be null");
		this.msoftOpenAiClient = msoftOpenAiClient;
	}

	@Override
	public String generate(String text) {
		ChatMessage chatMessage = new ChatMessage(ChatRole.USER, text);

		ChatCompletionsOptions options = new ChatCompletionsOptions(List.of(chatMessage));
		options.setTemperature(this.getTemperature());
		options.setModel(this.getModel());

		ChatCompletions chatCompletions = this.msoftOpenAiClient.getChatCompletions(this.getModel(), options);
		StringBuilder sb = new StringBuilder();
		for (ChatChoice choice : chatCompletions.getChoices()) {
			if (choice.getMessage() != null && choice.getMessage().getContent() != null) {
				sb.append(choice.getMessage().getContent());
			}
		}
		return sb.toString();
	}

	@Override
	public LLMResult generate(Prompt... prompts) {
		throw new RuntimeException("Method LLMResult generate(Prompt... prompts) not implemented.");
	}

	public Double getTemperature() {
		return temperature;
	}

	public void setTemperature(Double temperature) {
		this.temperature = temperature;
	}

	public String getModel() {
		return model;
	}

	public void setModel(String model) {
		this.model = model;
	}

}
