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

package org.springframework.ai.audio.tts.observation;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.audio.tts.TextToSpeechPrompt;
import org.springframework.ai.audio.tts.TextToSpeechResponse;
import org.springframework.ai.model.observation.ModelObservationContext;
import org.springframework.ai.observation.AiOperationMetadata;
import org.springframework.ai.observation.conventions.AiOperationType;
import org.springframework.util.Assert;

/**
 * Context used to store metadata for text-to-speech model exchanges.
 *
 * @author Olivier Le Quellec
 * @since 2.0.2
 */
public class TextToSpeechModelObservationContext
		extends ModelObservationContext<TextToSpeechPrompt, TextToSpeechResponse> {

	TextToSpeechModelObservationContext(TextToSpeechPrompt textToSpeechPrompt, String provider) {
		super(textToSpeechPrompt,
				AiOperationMetadata.builder()
					.operationType(AiOperationType.TEXT_TO_SPEECH.value())
					.provider(provider)
					.build());
		Assert.notNull(textToSpeechPrompt.getOptions(), "text to speech options cannot be null");
	}

	public static Builder builder() {
		return new Builder();
	}

	public String getOperationType() {
		return AiOperationType.TEXT_TO_SPEECH.value();
	}

	public static final class Builder {

		private @Nullable TextToSpeechPrompt textToSpeechPrompt;

		private @Nullable String provider;

		private Builder() {
		}

		public Builder textToSpeechPrompt(TextToSpeechPrompt textToSpeechPrompt) {
			this.textToSpeechPrompt = textToSpeechPrompt;
			return this;
		}

		public Builder provider(String provider) {
			this.provider = provider;
			return this;
		}

		public TextToSpeechModelObservationContext build() {
			Assert.notNull(this.textToSpeechPrompt, "textToSpeechPrompt cannot be null");
			Assert.hasText(this.provider, "provider cannot be null or empty");
			return new TextToSpeechModelObservationContext(this.textToSpeechPrompt, this.provider);
		}

	}

}
