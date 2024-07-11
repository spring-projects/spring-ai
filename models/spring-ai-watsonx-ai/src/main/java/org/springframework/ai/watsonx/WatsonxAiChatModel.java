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

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.model.ChatModel;
import reactor.core.publisher.Flux;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.model.StreamingChatModel;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.watsonx.api.WatsonxAiApi;
import org.springframework.ai.watsonx.api.WatsonxAiChatRequest;
import org.springframework.ai.watsonx.api.WatsonxAiChatResponse;
import org.springframework.ai.watsonx.utils.MessageToPromptConverter;
import org.springframework.util.Assert;

/**
 * {@link ChatModel} implementation for {@literal watsonx.ai}.
 * <p>
 * watsonx.ai allows developers to use large language models within a SaaS service. It
 * supports multiple open-source models as well as IBM created models.
 * <p>
 * Please refer to the <a href=
 * "https://dataplatform.cloud.ibm.com/docs/content/wsj/analyze-data/fm-models.html?context=wx">watsonx.ai
 * models</a> for the most up-to-date information about the available models.
 *
 * @author Pablo Sanchidrian Herrera
 * @author John Jario Moreno Rojas
 * @author Christian Tzolov
 * @since 1.0.0
 */
public class WatsonxAiChatModel implements ChatModel, StreamingChatModel {

	private final WatsonxAiApi watsonxAiApi;

	private final WatsonxAiChatOptions defaultOptions;

	public WatsonxAiChatModel(WatsonxAiApi watsonxAiApi) {
		this(watsonxAiApi,
				WatsonxAiChatOptions.builder()
					.withTemperature(0.7)
					.withTopP(1.0)
					.withTopK(50)
					.withDecodingMethod("greedy")
					.withMaxNewTokens(20)
					.withMinNewTokens(0)
					.withRepetitionPenalty(1.0)
					.withStopSequences(List.of())
					.build());
	}

	public WatsonxAiChatModel(WatsonxAiApi watsonxAiApi, WatsonxAiChatOptions defaultOptions) {
		Assert.notNull(watsonxAiApi, "watsonxAiApi cannot be null");
		Assert.notNull(defaultOptions, "defaultOptions cannot be null");
		this.watsonxAiApi = watsonxAiApi;
		this.defaultOptions = defaultOptions;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		WatsonxAiChatRequest request = request(prompt);

		WatsonxAiChatResponse response = this.watsonxAiApi.generate(request).getBody();
		var generation = new Generation(new AssistantMessage(response.results().get(0).generatedText()),
				ChatGenerationMetadata.from(response.results().get(0).stopReason(), response.system()));

		return new ChatResponse(List.of(generation));
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {

		WatsonxAiChatRequest request = request(prompt);

		Flux<WatsonxAiChatResponse> response = this.watsonxAiApi.generateStreaming(request);

		return response.map(chunk -> {
			String generatedText = chunk.results().get(0).generatedText();
			AssistantMessage assistantMessage = new AssistantMessage(generatedText);

			ChatGenerationMetadata metadata = ChatGenerationMetadata.NULL;
			if (chunk.system() != null) {
				metadata = ChatGenerationMetadata.from(chunk.results().get(0).stopReason(), chunk.system());
			}

			Generation generation = new Generation(assistantMessage, metadata);
			return new ChatResponse(List.of(generation));
		});
	}

	public WatsonxAiChatRequest request(Prompt prompt) {

		WatsonxAiChatOptions options = WatsonxAiChatOptions.builder().build();

		if (this.defaultOptions != null) {
			options = ModelOptionsUtils.merge(options, this.defaultOptions, WatsonxAiChatOptions.class);
		}

		if (prompt.getOptions() != null) {
			if (prompt.getOptions() instanceof WatsonxAiChatOptions runtimeOptions) {
				options = ModelOptionsUtils.merge(runtimeOptions, options, WatsonxAiChatOptions.class);
			}
			else {
				var updatedRuntimeOptions = ModelOptionsUtils.copyToTarget(prompt.getOptions(), ChatOptions.class,
						WatsonxAiChatOptions.class);

				options = ModelOptionsUtils.merge(updatedRuntimeOptions, options, WatsonxAiChatOptions.class);
			}
		}

		Map<String, Object> parameters = options.toMap();

		final String convertedPrompt = MessageToPromptConverter.create()
			.withAssistantPrompt("")
			.withHumanPrompt("")
			.toPrompt(prompt.getInstructions());

		return WatsonxAiChatRequest.builder(convertedPrompt).withParameters(parameters).build();
	}

	@Override
	public ChatOptions getDefaultOptions() {
		return WatsonxAiChatOptions.fromOptions(this.defaultOptions);
	}

}