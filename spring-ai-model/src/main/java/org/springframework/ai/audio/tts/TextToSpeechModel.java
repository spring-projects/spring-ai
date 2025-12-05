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

import org.springframework.ai.model.Model;
import org.springframework.ai.model.ModelResult;

/**
 * Interface for the text to speech model.
 *
 * @author Alexandros Pappas
 */
public interface TextToSpeechModel extends Model<TextToSpeechPrompt, TextToSpeechResponse>, StreamingTextToSpeechModel {

	default byte[] call(String text) {
		TextToSpeechPrompt prompt = new TextToSpeechPrompt(text);
		ModelResult<byte[]> result = call(prompt).getResult();
		if (result == null) {
			return new byte[0];
		}
		byte[] output = result.getOutput();
		return (output != null) ? output : new byte[0];
	}

	default byte[] call(String text, TextToSpeechOptions options) {
		TextToSpeechPrompt prompt = new TextToSpeechPrompt(text, options);
		ModelResult<byte[]> result = call(prompt).getResult();
		if (result == null) {
			return new byte[0];
		}
		byte[] output = result.getOutput();
		return (output != null) ? output : new byte[0];
	}

	@Override
	TextToSpeechResponse call(TextToSpeechPrompt prompt);

	default TextToSpeechOptions getDefaultOptions() {
		return TextToSpeechOptions.builder().build();
	}

}
