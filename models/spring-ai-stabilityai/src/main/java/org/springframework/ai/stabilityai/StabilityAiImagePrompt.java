package org.springframework.ai.stabilityai;

import org.springframework.ai.model.ModelRequest;
import org.springframework.ai.stabilityai.api.StabilityAiImageOptions;
import org.springframework.util.Assert;

import java.util.List;
import java.util.Objects;

public class StabilityAiImagePrompt implements ModelRequest<List<StabilityAiImageMessage>> {

	private final List<StabilityAiImageMessage> messages;

	private StabilityAiImageOptions options;

	public StabilityAiImagePrompt(List<StabilityAiImageMessage> messages, StabilityAiImageOptions options) {
		Assert.notNull(messages, "Prompt messages should not be null");
		Assert.notNull(options, "Prompt options should not be null");
		this.messages = messages;
		this.options = options;
	}

	@Override
	public List<StabilityAiImageMessage> getInstructions() {
		return this.messages;
	}

	@Override
	public StabilityAiImageOptions getOptions() {
		return this.options;
	}

	@Override
	public String toString() {
		return "StabilityAiImagePrompt{" + "messages=" + messages + ", options=" + options + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof StabilityAiImagePrompt that))
			return false;
		return Objects.equals(messages, that.messages) && Objects.equals(options, that.options);
	}

	@Override
	public int hashCode() {
		return Objects.hash(messages, options);
	}

}
