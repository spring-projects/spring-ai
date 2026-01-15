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

import java.util.Arrays;
import java.util.Objects;

import org.springframework.ai.model.ModelResult;
import org.springframework.ai.model.ResultMetadata;

/**
 * Implementation of the {@link ModelResult} interface for the speech model.
 *
 * @author Alexandros Pappas
 */
public class Speech implements ModelResult<byte[]> {

	private final byte[] speech;

	public Speech(byte[] speech) {
		this.speech = speech;
	}

	@Override
	public byte[] getOutput() {
		return this.speech;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof Speech speech1)) {
			return false;
		}
		return Arrays.equals(this.speech, speech1.speech);
	}

	@Override
	public int hashCode() {
		return Objects.hash(Arrays.hashCode(this.speech));
	}

	@Override
	public String toString() {
		return "Speech{" + "speech=" + Arrays.toString(this.speech) + '}';
	}

	@Override
	public ResultMetadata getMetadata() {
		return new ResultMetadata() {
		};
	}

}
