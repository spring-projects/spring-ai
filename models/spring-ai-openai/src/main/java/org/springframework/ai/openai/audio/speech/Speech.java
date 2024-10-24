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

import java.util.Arrays;
import java.util.Objects;

import org.springframework.ai.model.ModelResult;
import org.springframework.ai.openai.metadata.audio.OpenAiAudioSpeechMetadata;
import org.springframework.lang.Nullable;

/**
 * The Speech class represents the result of speech synthesis from an AI model. It
 * implements the ModelResult interface with the output type of byte array.
 *
 * @author Ahmed Yousri
 * @since 1.0.0-M1
 */
public class Speech implements ModelResult<byte[]> {

	private final byte[] audio;

	private OpenAiAudioSpeechMetadata speechMetadata;

	public Speech(byte[] audio) {
		this.audio = audio;
	}

	@Override
	public byte[] getOutput() {
		return this.audio;
	}

	@Override
	public OpenAiAudioSpeechMetadata getMetadata() {
		return this.speechMetadata != null ? this.speechMetadata : OpenAiAudioSpeechMetadata.NULL;
	}

	public Speech withSpeechMetadata(@Nullable OpenAiAudioSpeechMetadata speechMetadata) {
		this.speechMetadata = speechMetadata;
		return this;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Speech that)) {
			return false;
		}
		return Arrays.equals(this.audio, that.audio) && Objects.equals(this.speechMetadata, that.speechMetadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(Arrays.hashCode(this.audio), this.speechMetadata);
	}

	@Override
	public String toString() {
		return "Speech{" + "text=" + this.audio + ", speechMetadata=" + this.speechMetadata + '}';
	}

}
