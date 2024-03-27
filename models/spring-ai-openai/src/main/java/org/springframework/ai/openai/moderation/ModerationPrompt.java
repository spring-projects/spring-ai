package org.springframework.ai.openai.moderation;

import org.springframework.ai.model.ModelOptions;
import org.springframework.ai.model.ModelRequest;
import org.springframework.ai.openai.OpenAiModerationOptions;

/**
 * @author Ricken Bazolo
 */
public class ModerationPrompt implements ModelRequest<String> {

	private final String input;

	private final OpenAiModerationOptions options;

	public ModerationPrompt(String input) {
		this(input, OpenAiModerationOptions.builder().build());
	}

	public ModerationPrompt(String input, OpenAiModerationOptions options) {
		this.input = input;
		this.options = options;
	}

	@Override
	public String getInstructions() {
		return this.input;
	}

	@Override
	public ModelOptions getOptions() {
		return this.options;
	}

}
