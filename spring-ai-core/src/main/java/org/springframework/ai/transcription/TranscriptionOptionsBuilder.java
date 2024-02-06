/*
 * Copyright 2024-2024 the original author or authors.
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

package org.springframework.ai.transcription;

public class TranscriptionOptionsBuilder {

	private class TranscriptionOptionsImpl implements TranscriptionOptions {

		private Float temperature;

		@Override
		public Float getTemperature() {
			return temperature;
		}

		public void setTemperature(Float temperature) {
			this.temperature = temperature;
		}

	}

	private final TranscriptionOptionsImpl options = new TranscriptionOptionsImpl();

	private TranscriptionOptionsBuilder() {
	}

	public static TranscriptionOptionsBuilder builder() {
		return new TranscriptionOptionsBuilder();
	}

	public TranscriptionOptionsBuilder withTemperature(Float temperature) {
		options.setTemperature(temperature);
		return this;
	}

	public TranscriptionOptions build() {
		return options;
	}

}
