package org.springframework.ai.model;

import org.springframework.ai.audio.transcription.AudioTranscriptionOptions;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.core.io.Resource;

public interface TranscriptionModel extends Model<AudioTranscriptionPrompt, AudioTranscriptionResponse> {

	AudioTranscriptionResponse call(AudioTranscriptionPrompt transcriptionPrompt);

	default String transcribe(Resource resource) {
		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(resource);
		return this.call(prompt).getResult().getOutput();
	}

	default String transcribe(Resource resource, AudioTranscriptionOptions options) {
		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(resource, options);
		return this.call(prompt).getResult().getOutput();
	}

}
