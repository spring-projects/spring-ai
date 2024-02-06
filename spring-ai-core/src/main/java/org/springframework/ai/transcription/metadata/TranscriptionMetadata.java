package org.springframework.ai.transcription.metadata;

import org.springframework.ai.model.ResultMetadata;

public interface TranscriptionMetadata extends ResultMetadata {

	TranscriptionMetadata NULL = TranscriptionMetadata.create();

	/**
	 * Factory method used to construct a new {@link TranscriptionMetadata}
	 * @return a new {@link TranscriptionMetadata}
	 */
	static TranscriptionMetadata create() {
		return new TranscriptionMetadata() {
		};
	}

}
