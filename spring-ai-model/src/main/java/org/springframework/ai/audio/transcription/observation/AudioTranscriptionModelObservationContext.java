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

import org.jspecify.annotations.Nullable;

import org.springframework.ai.audio.transcription.AudioTranscriptionPrompt;
import org.springframework.ai.audio.transcription.AudioTranscriptionResponse;
import org.springframework.ai.model.observation.ModelObservationContext;
import org.springframework.ai.observation.AiOperationMetadata;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.util.Assert;

/**
 * Context used to store metadata for audio transcription model exchanges.
 *
 * @author Olivier Le Quellec
 * @since 2.0.1
 */
public class AudioTranscriptionModelObservationContext
		extends ModelObservationContext<AudioTranscriptionPrompt, AudioTranscriptionResponse> {

	AudioTranscriptionModelObservationContext(AudioTranscriptionPrompt transcriptionPrompt, String provider) {
		super(transcriptionPrompt,
				AiOperationMetadata.builder()
					.operationType(AiOperationType.TRANSCRIPTION.value())
					.provider(provider)
					.build());
	}

	public static Builder builder() {
		return new Builder();
	}

	public String getOperationType() {
		return AiOperationType.TRANSCRIPTION.value();
	}

	public static final class Builder {

		private @Nullable AudioTranscriptionPrompt transcriptionPrompt;

		private @Nullable String provider;

		private Builder() {
		}

		public Builder transcriptionPrompt(AudioTranscriptionPrompt transcriptionPrompt) {
			this.transcriptionPrompt = transcriptionPrompt;
			return this;
		}

		public Builder provider(String provider) {
			this.provider = provider;
			return this;
		}

		public AudioTranscriptionModelObservationContext build() {
			Assert.notNull(this.transcriptionPrompt, "transcriptionPrompt cannot be null");
			Assert.hasText(this.provider, "provider cannot be null or empty");
			return new AudioTranscriptionModelObservationContext(this.transcriptionPrompt, this.provider);
		}

	}

}
