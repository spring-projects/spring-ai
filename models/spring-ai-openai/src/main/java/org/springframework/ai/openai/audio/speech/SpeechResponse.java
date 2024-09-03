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

import org.springframework.ai.model.ModelResponse;
import org.springframework.ai.openai.metadata.audio.OpenAiAudioSpeechResponseMetadata;

import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Creates a new instance of SpeechResponse with the given speech result.
 *
 * @author Ahmed Yousri
 * @since 1.0.0-M1
 */
public class SpeechResponse implements ModelResponse<Speech> {

	private final Speech speech;

	private final OpenAiAudioSpeechResponseMetadata speechResponseMetadata;

	/**
	 * Creates a new instance of SpeechResponse with the given speech result.
	 * @param speech the speech result to be set in the SpeechResponse
	 * @see Speech
	 */
	public SpeechResponse(Speech speech) {
		this(speech, OpenAiAudioSpeechResponseMetadata.NULL);
	}

	/**
	 * Creates a new instance of SpeechResponse with the given speech result and speech
	 * response metadata.
	 * @param speech the speech result to be set in the SpeechResponse
	 * @param speechResponseMetadata the speech response metadata to be set in the
	 * SpeechResponse
	 * @see Speech
	 * @see OpenAiAudioSpeechResponseMetadata
	 */
	public SpeechResponse(Speech speech, OpenAiAudioSpeechResponseMetadata speechResponseMetadata) {
		this.speech = speech;
		this.speechResponseMetadata = speechResponseMetadata;
	}

	@Override
	public Speech getResult() {
		return speech;
	}

	@Override
	public List<Speech> getResults() {
		return Collections.singletonList(speech);
	}

	@Override
	public OpenAiAudioSpeechResponseMetadata getMetadata() {
		return speechResponseMetadata;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof SpeechResponse that))
			return false;
		return Objects.equals(speech, that.speech)
				&& Objects.equals(speechResponseMetadata, that.speechResponseMetadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(speech, speechResponseMetadata);
	}

}