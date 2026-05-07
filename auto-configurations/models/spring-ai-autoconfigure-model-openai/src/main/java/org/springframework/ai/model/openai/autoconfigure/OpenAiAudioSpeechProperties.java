/*
 * Copyright 2023-present the original author or authors.
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

package org.springframework.ai.model.openai.autoconfigure;

import org.jspecify.annotations.Nullable;

import org.springframework.ai.openai.OpenAiAudioSpeechOptions;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.DeprecatedConfigurationProperty;

/**
 * OpenAI SDK Audio Speech autoconfiguration properties.
 *
 * @author Ahmed Yousri
 * @author Stefan Vassilev
 * @author Jonghoon Park
 * @author Ilayaperumal Gopinathan
 */
@ConfigurationProperties(OpenAiAudioSpeechProperties.CONFIG_PREFIX)
public class OpenAiAudioSpeechProperties extends AbstractOpenAiProperties {

	public static final String CONFIG_PREFIX = "spring.ai.openai.audio.speech";

	public static final String DEFAULT_SPEECH_MODEL = OpenAiAudioSpeechOptions.DEFAULT_SPEECH_MODEL;

	private @Nullable String model = DEFAULT_SPEECH_MODEL;

	private @Nullable String input;

	private String voice = OpenAiAudioSpeechOptions.DEFAULT_VOICE;

	private String responseFormat = OpenAiAudioSpeechOptions.DEFAULT_RESPONSE_FORMAT;

	private Double speed = OpenAiAudioSpeechOptions.DEFAULT_SPEED;

	public @Nullable String getModel() {
		return this.model;
	}

	public void setModel(@Nullable String model) {
		this.model = model;
	}

	public @Nullable String getInput() {
		return this.input;
	}

	public void setInput(@Nullable String input) {
		this.input = input;
	}

	public String getVoice() {
		return this.voice;
	}

	public void setVoice(String voice) {
		this.voice = voice;
	}

	public String getResponseFormat() {
		return this.responseFormat;
	}

	public void setResponseFormat(String responseFormat) {
		this.responseFormat = responseFormat;
	}

	public Double getSpeed() {
		return this.speed;
	}

	public void setSpeed(Double speed) {
		this.speed = speed;
	}

	public OpenAiAudioSpeechOptions toOptions() {
		OpenAiAudioSpeechOptions.Builder builder = OpenAiAudioSpeechOptions.builder();
		builder.model(this.getModel());
		if (this.input != null) {
			builder.input(this.input);
		}
		if (this.voice != null) {
			builder.voice(this.voice);
		}
		if (this.responseFormat != null) {
			builder.responseFormat(this.responseFormat);
		}
		if (this.speed != null) {
			builder.speed(this.speed);
		}
		return builder.build();
	}

	private Options options = new Options();

	@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.audio.speech")
	@Deprecated(since = "2.0.0", forRemoval = true)
	public Options getOptions() {
		return this.options;
	}

	public void setOptions(Options options) {
		this.options = options;
	}

	public class Options {

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.audio.speech.model")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getModel() {
			return OpenAiAudioSpeechProperties.this.getModel();
		}

		public void setModel(@Nullable String model) {
			OpenAiAudioSpeechProperties.this.setModel(model);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.audio.speech.input")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public @Nullable String getInput() {
			return OpenAiAudioSpeechProperties.this.getInput();
		}

		public void setInput(@Nullable String input) {
			OpenAiAudioSpeechProperties.this.setInput(input);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.audio.speech.voice")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public String getVoice() {
			return OpenAiAudioSpeechProperties.this.getVoice();
		}

		public void setVoice(String voice) {
			OpenAiAudioSpeechProperties.this.setVoice(voice);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.audio.speech.response-format")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public String getResponseFormat() {
			return OpenAiAudioSpeechProperties.this.getResponseFormat();
		}

		public void setResponseFormat(String responseFormat) {
			OpenAiAudioSpeechProperties.this.setResponseFormat(responseFormat);
		}

		@DeprecatedConfigurationProperty(replacement = "spring.ai.openai.audio.speech.speed")
		@Deprecated(since = "2.0.0", forRemoval = true)
		public Double getSpeed() {
			return OpenAiAudioSpeechProperties.this.getSpeed();
		}

		public void setSpeed(Double speed) {
			OpenAiAudioSpeechProperties.this.setSpeed(speed);
		}

	}

}
