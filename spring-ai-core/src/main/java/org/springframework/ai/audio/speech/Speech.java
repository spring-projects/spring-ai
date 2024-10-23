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
package org.springframework.ai.audio.speech;

import org.springframework.ai.model.ModelResult;
import org.springframework.util.Assert;

import java.util.Arrays;
import java.util.Objects;

/**
 * The result of speech synthesis from an AI model.
 *
 * @author Ahmed Yousri
 * @author Thomas Vitale
 * @since 1.0.0
 */
public class Speech implements ModelResult<byte[]> {

	private final byte[] audio;

	private final SpeechMetadata speechMetadata;

	public Speech(byte[] audio) {
		this(audio, SpeechMetadata.EMPTY);
	}

	public Speech(byte[] audio, SpeechMetadata speechMetadata) {
		Assert.notNull(audio, "audio cannot be null");
		Assert.notNull(speechMetadata, "speechMetadata cannot be null");
		this.audio = audio;
		this.speechMetadata = speechMetadata;
	}

	@Override
	public byte[] getOutput() {
		return this.audio;
	}

	@Override
	public SpeechMetadata getMetadata() {
		return speechMetadata;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o)
			return true;
		if (!(o instanceof Speech that))
			return false;
		return Arrays.equals(audio, that.audio) && Objects.equals(speechMetadata, that.speechMetadata);
	}

	@Override
	public int hashCode() {
		return Objects.hash(Arrays.hashCode(audio), speechMetadata);
	}

	@Override
	public String toString() {
		return "Speech{" + "text=<byte[]>, speechMetadata=" + speechMetadata + "}";
	}

}
