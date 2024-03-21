package org.springframework.ai.openai.moderation;

import org.springframework.ai.model.ModelClient;

/**
 * @author Ricken Bazolo
 */
@FunctionalInterface
public interface ModerationClient extends ModelClient<ModerationPrompt, ModerationResponse> {

	default ModerationResponse call(String message) {
		var prompt = new ModerationPrompt(message);
		return call(prompt);
	}

	@Override
	ModerationResponse call(ModerationPrompt request);

}
