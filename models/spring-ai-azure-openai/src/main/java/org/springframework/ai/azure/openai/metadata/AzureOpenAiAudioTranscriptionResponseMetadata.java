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
import org.springframework.ai.audio.transcription.metadata.StructuredResponse;
import org.springframework.lang.Nullable;
import org.springframework.util.Assert;

/**
 * Audio transcription metadata implementation for {@literal AzureOpenAI}.
 *
 * @author Piotr Olaszewski
 */
public class AzureOpenAiAudioTranscriptionResponseMetadata extends AudioTranscriptionResponseMetadata {

	protected static final String AI_METADATA_STRING = "{ @type: %1$s }";

	public static AzureOpenAiAudioTranscriptionResponseMetadata from(StructuredResponse structuredResponse) {
		Assert.notNull(structuredResponse, "AzureOpenAI Transcription must not be null");
		return new AzureOpenAiAudioTranscriptionResponseMetadata(structuredResponse);
	}

	public static AzureOpenAiAudioTranscriptionResponseMetadata from(String result) {
		Assert.notNull(result, "AzureOpenAI Transcription must not be null");
		return new AzureOpenAiAudioTranscriptionResponseMetadata();
	}

	private final StructuredResponse structuredResponse;

	protected AzureOpenAiAudioTranscriptionResponseMetadata() {
		this(null);
	}

	public AzureOpenAiAudioTranscriptionResponseMetadata(StructuredResponse structuredResponse) {
		this.structuredResponse = structuredResponse;
	}

	@Nullable
	public StructuredResponse getStructuredResponse() {
		return structuredResponse;
	}

	@Override
	public String toString() {
		return AI_METADATA_STRING.formatted(getClass().getName());
	}

}
