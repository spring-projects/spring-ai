package org.springframework.ai.openai.moderation;

import org.springframework.ai.model.ModelResponse;
import org.springframework.ai.model.ResponseMetadata;

import java.util.List;

/**
 * @author Ricken Bazolo
 */
public class ModerationResponse implements ModelResponse<ModerationGeneration> {

	private final ModerationGeneration moderationGeneration;

	private final ModerationGenerationMetadata metadata;

	public ModerationResponse(ModerationGeneration moderationGeneration) {
		this(moderationGeneration, ModerationGenerationMetadata.NULL);
	}

	public ModerationResponse(ModerationGeneration moderationGeneration, ModerationGenerationMetadata metadata) {
		this.moderationGeneration = moderationGeneration;
		this.metadata = metadata;
	}

	@Override
	public ModerationGeneration getResult() {
		return this.moderationGeneration;
	}

	@Override
	public List<ModerationGeneration> getResults() {
		return List.of(moderationGeneration);
	}

	@Override
	public ResponseMetadata getMetadata() {
		return this.metadata;
	}

}
