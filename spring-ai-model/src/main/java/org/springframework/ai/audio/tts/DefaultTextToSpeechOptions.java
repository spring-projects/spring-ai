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

import java.util.Objects;

import com.fasterxml.jackson.annotation.JsonInclude;

/**
 * Default implementation of the {@link TextToSpeechOptions} interface.
 *
 * @author Alexandros Pappas
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public final class DefaultTextToSpeechOptions implements TextToSpeechOptions {

	private final String model;

	private final String voice;

	private final String format;

	private final Double speed;

	private DefaultTextToSpeechOptions(String model, String voice, String format, Double speed) {
		this.model = model;
		this.voice = voice;
		this.format = format;
		this.speed = speed;
	}

	public static Builder builder() {
		return new Builder();
	}

	@Override
	public String getModel() {
		return this.model;
	}

	@Override
	public String getVoice() {
		return this.voice;
	}

	@Override
	public String getFormat() {
		return this.format;
	}

	@Override
	public Double getSpeed() {
		return this.speed;
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof DefaultTextToSpeechOptions that)) {
			return false;
		}
		return Objects.equals(this.model, that.model) && Objects.equals(this.voice, that.voice)
				&& Objects.equals(this.format, that.format) && Objects.equals(this.speed, that.speed);
	}

	@Override
	public int hashCode() {
		return Objects.hash(this.model, this.voice, this.format, this.speed);
	}

	@Override
	public String toString() {
		return "DefaultTextToSpeechOptions{" + "model='" + this.model + '\'' + ", voice='" + this.voice + '\''
				+ ", format='" + this.format + '\'' + ", speed=" + this.speed + '}';
	}

	@Override
	@SuppressWarnings("unchecked")
	public DefaultTextToSpeechOptions copy() {
		return new Builder(this).build();
	}

	public static class Builder implements TextToSpeechOptions.Builder {

		private String model;

		private String voice;

		private String format;

		private Double speed;

		public Builder() {
		}

		private Builder(DefaultTextToSpeechOptions options) {
			this.model = options.model;
			this.voice = options.voice;
			this.format = options.format;
			this.speed = options.speed;
		}

		@Override
		public Builder model(String model) {
			this.model = model;
			return this;
		}

		@Override
		public Builder voice(String voice) {
			this.voice = voice;
			return this;
		}

		@Override
		public Builder format(String format) {
			this.format = format;
			return this;
		}

		@Override
		public Builder speed(Double speed) {
			this.speed = speed;
			return this;
		}

		public DefaultTextToSpeechOptions build() {
			return new DefaultTextToSpeechOptions(this.model, this.voice, this.format, this.speed);
		}

	}

}
