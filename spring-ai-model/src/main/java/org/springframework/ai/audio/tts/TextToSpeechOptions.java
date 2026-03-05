/*
 * Copyright 2025-2025 the original author or authors.
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

package org.springframework.ai.audio.tts;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.model.ModelOptions;

/**
 * Interface for text-to-speech model options. Defines the common, portable options that
 * should be supported by all implementations.
 *
 * @author Alexandros Pappas
 */
public interface TextToSpeechOptions extends ModelOptions {

	/**
	 * Creates a new {@link TextToSpeechOptions.Builder} to create the default
	 * {@link TextToSpeechOptions}.
	 * @return Returns a new {@link TextToSpeechOptions.Builder}.
	 */
	static TextToSpeechOptions.Builder builder() {
		return new DefaultTextToSpeechOptions.Builder();
	}

	/**
	 * Returns the model to use for text-to-speech.
	 * @return The model name.
	 */
	@Nullable String getModel();

	/**
	 * Returns the voice to use for text-to-speech.
	 * @return The voice identifier.
	 */
	@Nullable String getVoice();

	/**
	 * Returns the output format for the generated audio.
	 * @return The output format (e.g., "mp3", "wav").
	 */
	@Nullable String getFormat();

	/**
	 * Returns the speed of the generated speech.
	 * @return The speech speed.
	 */
	@Nullable Double getSpeed();

	/**
	 * Returns a copy of this {@link TextToSpeechOptions}.
	 * @return a copy of this {@link TextToSpeechOptions}
	 */
	<T extends TextToSpeechOptions> T copy();

	/**
	 * Builder for {@link TextToSpeechOptions}.
	 */
	interface Builder {

		/**
		 * Sets the model to use for text-to-speech.
		 * @param model The model name.
		 * @return This builder.
		 */
		Builder model(String model);

		/**
		 * Sets the voice to use for text-to-speech.
		 * @param voice The voice identifier.
		 * @return This builder.
		 */
		Builder voice(String voice);

		/**
		 * Sets the output format for the generated audio.
		 * @param format The output format (e.g., "mp3", "wav").
		 * @return This builder.
		 */
		Builder format(String format);

		/**
		 * Sets the speed of the generated speech.
		 * @param speed The speech speed.
		 * @return This builder.
		 */
		Builder speed(Double speed);

		/**
		 * Builds the {@link TextToSpeechOptions}.
		 * @return The {@link TextToSpeechOptions}.
		 */
		TextToSpeechOptions build();

	}

}
