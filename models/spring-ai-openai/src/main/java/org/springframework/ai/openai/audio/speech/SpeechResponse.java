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

/**
 * @author Ahmed Yousri
 */

public class SpeechResponse implements ModelResponse<Speech> {

	private final Speech speech;

	private final OpenAiAudioSpeechResponseMetadata speechResponseMetadata;

	public SpeechResponse(Speech speech) {
		this(speech, OpenAiAudioSpeechResponseMetadata.NULL);
	}

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

}