/*
 * Copyright 2023-present the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.springframework.ai.audio.transcription;

import org.jspecify.annotations.Nullable;
import reactor.core.publisher.Flux;

import org.springframework.ai.model.StreamingModel;
import org.springframework.core.io.Resource;

/**
 * Interface for the streaming transcription model. This extends {@link StreamingModel} to
 * provide a streaming API for speech-to-text transcription, where audio input is
 * converted to text output incrementally.
 *
 * @author guan xu
 * @since 2.0.1
 */
@FunctionalInterface
public interface StreamingTranscriptionModel
		extends StreamingModel<AudioTranscriptionPrompt, AudioTranscriptionResponse> {

	/**
	 * Streams a transcription request to the model, returning the transcription results
	 * incrementally as they become available.
	 * @param transcriptionPrompt the transcription prompt containing the audio input and
	 * options
	 * @return a {@link Flux} of {@link AudioTranscriptionResponse} chunks representing
	 * incremental transcription results
	 */
	Flux<AudioTranscriptionResponse> stream(AudioTranscriptionPrompt transcriptionPrompt);

	/**
	 * A convenience method for streaming the transcription of an audio resource.
	 * @param resource the audio resource to transcribe
	 * @return a {@link Flux} of transcribed text segments
	 */
	default Flux<String> stream(Resource resource) {
		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(resource);
		return stream(prompt).map(response -> (response.getResult() == null || response.getResult().getOutput() == null)
				? "" : response.getResult().getOutput());
	}

	/**
	 * A convenience method for streaming the transcription of an audio resource with the
	 * given options.
	 * @param resource the audio resource to transcribe
	 * @param options the transcription options
	 * @return a {@link Flux} of transcribed text segments
	 */
	default Flux<String> stream(Resource resource, @Nullable AudioTranscriptionOptions options) {
		AudioTranscriptionPrompt prompt = new AudioTranscriptionPrompt(resource, options);
		return stream(prompt).map(response -> (response.getResult() == null || response.getResult().getOutput() == null)
				? "" : response.getResult().getOutput());
	}

}
