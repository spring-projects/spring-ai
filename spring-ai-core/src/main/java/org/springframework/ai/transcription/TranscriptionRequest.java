package org.springframework.ai.transcription;

import org.springframework.ai.model.ModelOptions;
import org.springframework.ai.model.ModelRequest;
import org.springframework.core.io.Resource;

public class TranscriptionRequest implements ModelRequest<Resource> {

	private Resource audioResource;

	private ModelOptions modelOptions;

	public TranscriptionRequest(Resource audioResource) {
		this.audioResource = audioResource;
	}

	public TranscriptionRequest(Resource audioResource, ModelOptions modelOptions) {
		this.audioResource = audioResource;
		this.modelOptions = modelOptions;
	}

	@Override
	public Resource getInstructions() {
		return audioResource;
	}

	@Override
	public ModelOptions getOptions() {
		return modelOptions;
	}

}
