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
 * Context used to store metadata for transcription model exchanges.
 *
 * @author Christian Tzolov
 * @since 2.0.2
 */
public class TranscriptionModelObservationContext
		extends ModelObservationContext<AudioTranscriptionPrompt, AudioTranscriptionResponse> {

	private final boolean streaming;

	TranscriptionModelObservationContext(AudioTranscriptionPrompt prompt, String provider, boolean streaming) {
		super(prompt,
				AiOperationMetadata.builder()
					.operationType(AiOperationType.TRANSCRIPTION.value())
					.provider(provider)
					.build());
		this.streaming = streaming;
	}

	/**
	 * Whether the request was made in streaming mode.
	 * @return {@code true} for a {@code stream()} call, {@code false} for a
	 * {@code call()}
	 */
	public boolean isStreaming() {
		return this.streaming;
	}

	public static Builder builder() {
		return new Builder();
	}

	public String getOperationType() {
		return AiOperationType.TRANSCRIPTION.value();
	}

	public static final class Builder {

		private @Nullable AudioTranscriptionPrompt prompt;

		private @Nullable String provider;

		private boolean streaming;

		private Builder() {
		}

		public Builder prompt(AudioTranscriptionPrompt prompt) {
			this.prompt = prompt;
			return this;
		}

		public Builder provider(String provider) {
			this.provider = provider;
			return this;
		}

		/**
		 * Marks the request as made in streaming mode. Defaults to {@code false}.
		 * @param streaming whether the request is a {@code stream()} call
		 * @return this builder
		 */
		public Builder streaming(boolean streaming) {
			this.streaming = streaming;
			return this;
		}

		public TranscriptionModelObservationContext build() {
			Assert.notNull(this.prompt, "prompt cannot be null");
			Assert.hasText(this.provider, "provider cannot be null or empty");
			return new TranscriptionModelObservationContext(this.prompt, this.provider, this.streaming);
		}

	}

}
