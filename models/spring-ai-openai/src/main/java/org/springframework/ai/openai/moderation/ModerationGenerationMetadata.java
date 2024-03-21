package org.springframework.ai.openai.moderation;

import org.springframework.ai.model.ResponseMetadata;

/**
 * @author Ricken Bazolo
 */
public interface ModerationGenerationMetadata extends ResponseMetadata {

	ModerationGenerationMetadata NULL = new ModerationGenerationMetadata() {
	};

}
