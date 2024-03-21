package org.springframework.ai.openai.moderation;

import org.springframework.ai.model.ModelResult;
import org.springframework.ai.model.ResultMetadata;
import org.springframework.ai.openai.api.OpenAiModerationApi.ModerationObject;

/**
 * @author Ricken Bazolo
 */
public class ModerationGeneration implements ModelResult<ModerationObject> {

	private final ModerationObject moderationObject;

	public ModerationGeneration(ModerationObject moderationObject) {
		this.moderationObject = moderationObject;
	}

	@Override
	public ModerationObject getOutput() {
		return this.moderationObject;
	}

	@Override
	public ResultMetadata getMetadata() {
		return null;
	}

}
