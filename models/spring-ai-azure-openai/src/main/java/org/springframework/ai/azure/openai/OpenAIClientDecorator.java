package org.springframework.ai.azure.openai;

import com.azure.ai.openai.OpenAIClient;
import com.azure.ai.openai.models.ChatCompletions;
import com.azure.ai.openai.models.ChatCompletionsOptions;
import com.azure.core.annotation.ReturnType;
import com.azure.core.annotation.ServiceMethod;
import com.azure.core.util.IterableStream;
import org.springframework.ai.azure.openai.dto.AccessibleChatCompletions;
import reactor.core.publisher.Flux;

import java.util.List;

public class OpenAIClientDecorator {

	private final OpenAIClient inner;

	public OpenAIClientDecorator(OpenAIClient inner) {
		this.inner = inner;
	}

	@ServiceMethod(returns = ReturnType.SINGLE)
	public AccessibleChatCompletions getChatCompletions(String deploymentOrModelName,
			ChatCompletionsOptions chatCompletionsOptions) {
		final var chatCompletions = inner.getChatCompletions(deploymentOrModelName, chatCompletionsOptions);
		return AccessibleChatCompletions.from(chatCompletions);
	}

	@ServiceMethod(returns = ReturnType.COLLECTION)
	public IterableStream<AccessibleChatCompletions> getChatCompletionsStream(String deploymentOrModelName,
			ChatCompletionsOptions chatCompletionsOptions) {
		final var chatCompletionsStream = inner.getChatCompletionsStream(deploymentOrModelName, chatCompletionsOptions);
		final var newFlux = Flux.fromStream(chatCompletionsStream.stream()).map(AccessibleChatCompletions::from);
		return new IterableStream<>(newFlux);
	}

}
