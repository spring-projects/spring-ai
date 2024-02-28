package org.springframework.ai.watsonx;

import org.springframework.ai.chat.ChatClient;
import org.springframework.ai.chat.ChatResponse;
import org.springframework.ai.chat.Generation;
import org.springframework.ai.chat.StreamingChatClient;
import org.springframework.ai.chat.metadata.ChatGenerationMetadata;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.model.ModelOptions;
import org.springframework.ai.model.ModelOptionsUtils;
import org.springframework.ai.watsonx.api.WatsonxAIApi;
import org.springframework.ai.watsonx.api.WatsonxAIOptions;
import org.springframework.ai.watsonx.api.WatsonxAIRequest;
import org.springframework.ai.watsonx.api.WatsonxAIResponse;
import org.springframework.ai.watsonx.utils.MessageToPromptConverter;
import org.springframework.util.StringUtils;
import reactor.core.publisher.Flux;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

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
 * @since 0.8.0
 */
public class WatsonxChatClient implements ChatClient, StreamingChatClient {

	private final WatsonxAIApi chatApi;

	public WatsonxChatClient(WatsonxAIApi watsonxAIApi) {
		this.chatApi = watsonxAIApi;
	}

	@Override
	public ChatResponse call(Prompt prompt) {

		WatsonxAIRequest request = request(prompt);

		WatsonxAIResponse response = this.chatApi.generate(request);
		var generator = new Generation(response.results().get(0).generatedText());

		generator = generator.withGenerationMetadata(
				ChatGenerationMetadata.from(response.results().get(0).stopReason(), response.system()));

		return new ChatResponse(List.of(generator));
	}

	@Override
	public Flux<ChatResponse> stream(Prompt prompt) {

		WatsonxAIRequest request = request(prompt);

		Flux<WatsonxAIResponse> response = this.chatApi.generateStreaming(request);

		return response.map(chunk -> {
			Generation generation = new Generation(chunk.results().get(0).generatedText());
			if (chunk.system() != null) {
				generation = generation.withGenerationMetadata(
						ChatGenerationMetadata.from(chunk.results().get(0).stopReason(), chunk.system()));
			}
			return new ChatResponse(List.of(generation));
		});
	}

	public static WatsonxAIRequest request(Prompt prompt) {

		final String convertedPrompt = MessageToPromptConverter.create()
			.withAssistantPrompt("")
			.withHumanPrompt("")
			.toPrompt(prompt.getInstructions());
		ModelOptions givenOptions = prompt.getOptions();

		WatsonxAIOptions runtimeOptions = Objects.nonNull(givenOptions)
				? ModelOptionsUtils.copyToTarget(givenOptions, ModelOptions.class, WatsonxAIOptions.class)
				: WatsonxAIOptions.create();

		Optional.ofNullable(Objects.requireNonNull(runtimeOptions).getModel())
			.orElseThrow(() -> new IllegalArgumentException("model is not set!"));

		Map<String, Object> parameters = runtimeOptions.toMap();

		return WatsonxAIRequest.builder(convertedPrompt).withParameters(parameters).build();
	}

}