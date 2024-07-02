package org.springframework.ai.chat.observation;

import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.micrometer.model.ModelResponseContext;

/**
 * Helper class to extract data from an LLM chat operation.
 *
 * @author Thomas Vitale
 */
public class ChatObservationDataExtractor {

	public static ModelResponseContext extractResponseData(ChatResponse chatResponse) {
		return ModelResponseContext.builder()
			.finishReasons(chatResponse.getResult().getMetadata().getFinishReason().toLowerCase())
			.responseId(chatResponse.getMetadata().getId())
			.responseModel(chatResponse.getMetadata().getModel())
			.completionTokens(Integer.parseInt(chatResponse.getMetadata().getUsage().getGenerationTokens().toString()))
			.promptTokens(Integer.parseInt(chatResponse.getMetadata().getUsage().getPromptTokens().toString()))
			.completion(chatResponse.getResult().getOutput().getContent())
			.build();
	}

}
