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
package org.springframework.ai.azure.openai.metadata;

import org.springframework.ai.audio.transcription.AudioTranscriptionResponseMetadata;
import org.springframework.ai.azure.openai.AzureOpenAiAudioTranscriptionOptions;
import org.springframework.ai.model.MutableResponseMetadata;
import org.springframework.util.Assert;

/**
 * Audio transcription metadata implementation for {@literal AzureOpenAI}.
 *
 * @author Piotr Olaszewski
 */
public class AzureOpenAiAudioTranscriptionResponseMetadata extends MutableResponseMetadata
		implements AudioTranscriptionResponseMetadata {

	protected static final String AI_METADATA_STRING = "{ @type: %1$s }";

	public static final AzureOpenAiAudioTranscriptionResponseMetadata NULL = new AzureOpenAiAudioTranscriptionResponseMetadata() {
	};

	public static AzureOpenAiAudioTranscriptionResponseMetadata from(
			AzureOpenAiAudioTranscriptionOptions.StructuredResponse result) {
		Assert.notNull(result, "AzureOpenAI Transcription must not be null");
		return new AzureOpenAiAudioTranscriptionResponseMetadata();
	}

	public static AzureOpenAiAudioTranscriptionResponseMetadata from(String result) {
		Assert.notNull(result, "AzureOpenAI Transcription must not be null");
		return new AzureOpenAiAudioTranscriptionResponseMetadata();
	}

	protected AzureOpenAiAudioTranscriptionResponseMetadata() {
	}

	@Override
	public String toString() {
		return AI_METADATA_STRING.formatted(getClass().getName());
	}

}
