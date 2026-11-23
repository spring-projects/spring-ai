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

package org.springframework.ai.audio.transcription.observation;

import io.micrometer.observation.Observation;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;

import org.springframework.ai.audio.transcription.AudioTranscription;
import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.core.io.ByteArrayResource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link TranscriptionModelTranscriptObservationHandler}.
 *
 * @author Christian Tzolov
 */
@ExtendWith(OutputCaptureExtension.class)
class TranscriptionModelTranscriptObservationHandlerTests {

	private final TranscriptionModelTranscriptObservationHandler observationHandler = new TranscriptionModelTranscriptObservationHandler();

	private static TranscriptionModelObservationContext context() {
		return TranscriptionModelObservationContext.builder()
			.prompt(new AudioTranscriptionPrompt(new ByteArrayResource(new byte[] { 1 })))
			.provider("superprovider")
			.build();
	}

	@Test
	void whenNotSupportedObservationContextThenReturnFalse() {
		assertThat(this.observationHandler.supportsContext(new Observation.Context())).isFalse();
	}

	@Test
	void whenSupportedObservationContextThenReturnTrue() {
		assertThat(this.observationHandler.supportsContext(context())).isTrue();
	}

	@Test
	void whenTranscriptThenOutputTranscript(CapturedOutput output) {
		var context = context();
		context.setResponse(new AudioTranscriptionResponse(new AudioTranscription("What is the weather?")));
		this.observationHandler.onStop(context);
		assertThat(output).containsIgnoringNewLines("""
				INFO  o.s.a.a.t.o.TranscriptionModelTranscriptObservationHandler -- Transcription Model Transcript:
				What is the weather?
				""");
	}

	@Test
	void whenNoResponseThenOutputNothing(CapturedOutput output) {
		this.observationHandler.onStop(context()); // e.g. the call failed
		assertThat(output).doesNotContain("Transcription Model Transcript");
	}

}
