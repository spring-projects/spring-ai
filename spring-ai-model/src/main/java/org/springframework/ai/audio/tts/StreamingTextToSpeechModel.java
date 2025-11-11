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

import reactor.core.publisher.Flux;

import org.springframework.ai.model.StreamingModel;

/**
 * Interface for the streaming text to speech model.
 *
 * @author Alexandros Pappas
 */
@FunctionalInterface
public interface StreamingTextToSpeechModel extends StreamingModel<TextToSpeechPrompt, TextToSpeechResponse> {

	default Flux<byte[]> stream(String text) {
		TextToSpeechPrompt prompt = new TextToSpeechPrompt(text);
		return stream(prompt).map(response -> (response.getResult() == null || response.getResult().getOutput() == null)
				? new byte[0] : response.getResult().getOutput());
	}

	default Flux<byte[]> stream(String text, TextToSpeechOptions options) {
		TextToSpeechPrompt prompt = new TextToSpeechPrompt(text, options);
		return stream(prompt).map(response -> (response.getResult() == null || response.getResult().getOutput() == null)
				? new byte[0] : response.getResult().getOutput());
	}

	@Override
	Flux<TextToSpeechResponse> stream(TextToSpeechPrompt prompt);

}
