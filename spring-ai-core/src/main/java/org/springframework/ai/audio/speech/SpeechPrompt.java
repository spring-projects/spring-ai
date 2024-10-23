/*
 * Copyright 2023 - 2024 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.springframework.ai.audio.speech;

import org.springframework.ai.model.ModelRequest;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

import java.util.Objects;

/**
 * Represents a request to a speech-synthesis AI model.
 *
 * @author Ahmed Yousri
 * @author Thomas Vitale
 * @since 1.0.0-M1
 */
public class SpeechPrompt implements ModelRequest<SpeechMessage> {

	private final SpeechOptions speechOptions;

	private final SpeechMessage message;

	public SpeechPrompt(SpeechMessage message, SpeechOptions speechOptions) {
		Assert.notNull(message, "message cannot be null");
		Assert.notNull(speechOptions, "speechOptions cannot be null");
		this.message = message;
		this.speechOptions = speechOptions;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public SpeechMessage getInstructions() {
		return this.message;
	}

	@Override
	public SpeechOptions getOptions() {
		return speechOptions;
	}

	@Override
	public String toString() {
		return "SpeechPrompt{" + "message=" + message + ", speechOptions=" + speechOptions + '}';
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof SpeechPrompt that))
			return false;
		return Objects.equals(speechOptions, that.speechOptions) && Objects.equals(message, that.message);
	}

	@Override
	public int hashCode() {
		return Objects.hash(speechOptions, message);
	}

	public static class Builder {

		@Nullable
		private SpeechMessage message;

		@Nullable
		private SpeechOptions speechOptions;

		private Builder() {
		}

		public Builder withMessage(String message) {
			this.message = new SpeechMessage(message);
			return this;
		}

		public Builder withMessage(SpeechMessage message) {
			this.message = message;
			return this;
		}

		public Builder withSpeechOptions(@Nullable SpeechOptions speechOptions) {
			this.speechOptions = speechOptions;
			return this;
		}

		public SpeechPrompt build() {
			Assert.notNull(message, "message cannot be null");
			var options = speechOptions == null ? SpeechOptionsBuilder.builder().build() : speechOptions;
			return new SpeechPrompt(message, options);
		}

	}

}
