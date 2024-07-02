package org.springframework.ai.openai.observation;

import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.micrometer.model.ModelRequestContext;
import org.springframework.ai.openai.api.OpenAiApi;

/**
 * Helper class to extract data from an OpenAI LLM interaction.
 *
 * @author Thomas Vitale
 */
public class OpenAiObservationDataExtractor {

	public static ModelRequestContext extractChatRequestData(OpenAiApi.ChatCompletionRequest request, Prompt prompt,
			String system, String operationName) {
		return ModelRequestContext.builder()
			.system(system)
			.model(request.model())
			.operationName(operationName)
			.frequencyPenalty(request.frequencyPenalty())
			.maxTokens(request.maxTokens())
			.presencePenalty(request.presencePenalty())
			.stopSequences(request.stop())
			.temperature(request.temperature())
			.topP(request.topP())
			.prompt(prompt.getContents())
			.build();
	}

}
