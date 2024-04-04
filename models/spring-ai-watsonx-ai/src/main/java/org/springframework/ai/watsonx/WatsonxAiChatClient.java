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
package org.springframework.ai.watsonx;

import java.util.List;
import java.util.Map;

import reactor.core.publisher.Flux;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.watsonx.api.WatsonxAiApi;
import org.springframework.ai.watsonx.api.WatsonxAiRequest;
import org.springframework.ai.watsonx.api.WatsonxAiResponse;
import org.springframework.ai.watsonx.utils.MessageToPromptConverter;
import org.springframework.util.Assert;

/**
 * {@link ChatClient} implementation for {@literal watsonx.ai}.
 *
 * watsonx.ai allows developers to use large language models within a SaaS service. It
 * supports multiple open-source models as well as IBM created models
 * [watsonx.ai](https://www.ibm.com/products/watsonx-ai). Please refer to the <a href=
 * "https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-models.html?context=wx">watsonx.ai
 * models</a> for the most up-to-date information about the available models.
 *
 * @author Pablo Sanchidrian Herrera
 * @author John Jario Moreno Rojas
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class WatsonxAiChatClient implements ChatClient, StreamingChatClient {

	private final WatsonxAiApi watsonxAiApi;

	private final WatsonxAiChatOptions defaultOptions;

	public WatsonxAiChatClient(WatsonxAiApi watsonxAiApi) {
		this(watsonxAiApi,
				WatsonxAiChatOptions.builder()
					.withTemperature(0.7f)
					.withTopP(1.0f)
					.withTopK(50)
					.withDecodingMethod("greedy")
					.withMaxNewTokens(20)
					.withMinNewTokens(0)
					.withRepetitionPenalty(1.0f)
					.build());
	}

	public WatsonxAiChatClient(WatsonxAiApi watsonxAiApi, WatsonxAiChatOptions defaultOptions) {
		Assert.notNull(watsonxAiApi, "watsonxAiApi cannot be null");
		Assert.notNull(defaultOptions, "defaultOptions cannot be null");
		this.watsonxAiApi = watsonxAiApi;
		this.defaultOptions = defaultOptions;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		WatsonxAiRequest request = request(prompt);

		WatsonxAiResponse response = this.watsonxAiApi.generate(request).getBody();
		var generator = new Generation(response.results().get(0).generatedText());

		generator = generator.withGenerationMetadata(
				ChatGenerationMetadata.from(response.results().get(0).stopReason(), response.system()));

		return new ChatResponse(List.of(generator));
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {

		WatsonxAiRequest request = request(prompt);

		Flux<WatsonxAiResponse> response = this.watsonxAiApi.generateStreaming(request);

		return response.map(chunk -> {
			Generation generation = new Generation(chunk.results().get(0).generatedText());
			if (chunk.system() != null) {
				generation = generation.withGenerationMetadata(
						ChatGenerationMetadata.from(chunk.results().get(0).stopReason(), chunk.system()));
			}
			return new ChatResponse(List.of(generation));
		});
	}

	public WatsonxAiRequest request(Prompt prompt) {

		WatsonxAiChatOptions options = WatsonxAiChatOptions.builder().build();

		if (this.defaultOptions != null) {
			options = ModelOptionsUtils.merge(options, this.defaultOptions, WatsonxAiChatOptions.class);
		}

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof ChatOptions runtimeOptions) {
				var updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(runtimeOptions, ChatOptions.class,
						WatsonxAiChatOptions.class);

				options = ModelOptionsUtils.merge(updatedRuntimeOptions, options, WatsonxAiChatOptions.class);
			}
			else {
				throw new IllegalArgumentException("Prompt options are not of type ChatOptions: "
						+ prompt.getOptions().getClass().getSimpleName());
			}
		}

		Map<String, Object> parameters = options.toMap();

		final String convertedPrompt = MessageToPromptConverter.create()
			.withAssistantPrompt("")
			.withHumanPrompt("")
			.toPrompt(prompt.getInstructions());

		return WatsonxAiRequest.builder(convertedPrompt).withParameters(parameters).build();
	}

}