/*
 * Copyright 2023-2024 the original author or authors.
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

package org.springframework.ai.openai.audio.speech;

import org.springframework.ai.model.Model;

/**
 * The {@link SpeechModel} interface provides a way to interact with the OpenAI
 * Text-to-Speech (TTS) API. It allows you to convert text input into lifelike spoken
 * audio.
 *
 * @author Ahmed Yousri
 * @since 1.0.0-M1
 * @deprecated Use {@link org.springframework.ai.audio.tts.TextToSpeechModel} from the
 * core package instead. This interface will be removed in a future release.
 */
@Deprecated
@FunctionalInterface
public interface SpeechModel extends Model<SpeechPrompt, SpeechResponse> {

	/**
	 * Generates spoken audio from the provided text message.
	 * @param message the text message to be converted to audio
	 * @return the resulting audio bytes
	 */
	default byte[] call(String message) {
		SpeechPrompt prompt = new SpeechPrompt(message);
		return call(prompt).getResult().getOutput();
	}

	/**
	 * Sends a speech request to the OpenAI TTS API and returns the resulting speech
	 * response.
	 * @param request the speech prompt containing the input text and other parameters
	 * @return the speech response containing the generated audio
	 */
	SpeechResponse call(SpeechPrompt request);

}
