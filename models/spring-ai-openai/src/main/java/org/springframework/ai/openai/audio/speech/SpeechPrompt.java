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
package org.springframework.ai.openai.audio.speech;

import org.springframework.ai.model.ModelOptions;
import org.springframework.ai.model.ModelRequest;
import org.springframework.ai.openai.OpenAiAudioSpeechOptions;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * The {@link SpeechPrompt} class represents a request to the OpenAI Text-to-Speech (TTS)
 * API. It contains a list of {@link SpeechMessage} objects, each representing a piece of
 * text to be converted to speech.
 *
 * @author Ahmed Yousri
 * @since 1.0.0-M1
 */
public class SpeechPrompt implements ModelRequest<SpeechMessage> {

	private OpenAiAudioSpeechOptions speechOptions;

	private final SpeechMessage message;

	public SpeechPrompt(String instructions) {
		this(new SpeechMessage(instructions), OpenAiAudioSpeechOptions.builder().build());
	}

	public SpeechPrompt(String instructions, OpenAiAudioSpeechOptions speechOptions) {
		this(new SpeechMessage(instructions), speechOptions);
	}

	public SpeechPrompt(SpeechMessage speechMessage) {
		this(speechMessage, OpenAiAudioSpeechOptions.builder().build());
	}

	public SpeechPrompt(SpeechMessage speechMessage, OpenAiAudioSpeechOptions speechOptions) {
		this.message = speechMessage;
		this.speechOptions = speechOptions;
	}

	@Override
	public SpeechMessage getInstructions() {
		return this.message;
	}

	@Override
	public ModelOptions getOptions() {
		return speechOptions;
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

}
