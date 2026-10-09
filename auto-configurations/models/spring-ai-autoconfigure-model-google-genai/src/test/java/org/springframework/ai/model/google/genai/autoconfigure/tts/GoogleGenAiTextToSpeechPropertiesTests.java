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

package org.springframework.ai.model.google.genai.autoconfigure.tts;

import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.google.genai.tts.GoogleGenAiAudioSpeechOptions;
import org.springframework.ai.google.genai.tts.GoogleGenAiAudioSpeechOptions.MultiSpeakerTurn;
import org.springframework.ai.google.genai.tts.GoogleGenAiAudioSpeechOptions.SpeakerVoiceConfig;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for Google GenAI Gemini-TTS properties binding.
 *
 * @author Olivier Le Quellec
 */
class GoogleGenAiTextToSpeechPropertiesTests {

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withUserConfiguration(PropertiesTestConfiguration.class);

	@Test
	void connectionPropertiesBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.tts.project-id=test-project",
					"spring.ai.google.genai.tts.location=eu",
					"spring.ai.google.genai.tts.credentials-uri=classpath:fake-credentials.json")
			.run(context -> {
				GoogleGenAiTextToSpeechConnectionProperties props = context
					.getBean(GoogleGenAiTextToSpeechConnectionProperties.class);
				assertThat(props.getProjectId()).isEqualTo("test-project");
				assertThat(props.getLocation()).isEqualTo("eu");
				assertThat(props.getCredentialsUri()).isNotNull();
			});
	}

	@Test
	void connectionPropertiesLocationDefaultsToGlobal() {
		this.contextRunner.run(context -> {
			GoogleGenAiTextToSpeechConnectionProperties props = context
				.getBean(GoogleGenAiTextToSpeechConnectionProperties.class);
			assertThat(props.getLocation()).isEqualTo("global");
		});
	}

	@Test
	void speechPropertiesBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.tts.model=gemini-2.5-pro-tts",
					"spring.ai.google.genai.tts.voice-name=Kore", "spring.ai.google.genai.tts.language-code=en",
					"spring.ai.google.genai.tts.style-prompt=Say the following in a curious way",
					"spring.ai.google.genai.tts.audio-encoding=PCM")
			.run(context -> {
				GoogleGenAiTextToSpeechProperties props = context.getBean(GoogleGenAiTextToSpeechProperties.class);
				GoogleGenAiAudioSpeechOptions options = props.toOptions();
				assertThat(options.getModel()).isEqualTo("gemini-2.5-pro-tts");
				assertThat(options.getVoiceName()).isEqualTo("Kore");
				assertThat(options.getLanguageCode()).isEqualTo("en");
				assertThat(options.getStylePrompt()).isEqualTo("Say the following in a curious way");
				assertThat(options.getAudioEncoding()).isEqualTo(GoogleGenAiAudioSpeechOptions.AudioEncoding.PCM);
			});
	}

	@Test
	void speakerVoiceConfigsBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.tts.speaker-voice-configs[0].speaker=Sam",
					"spring.ai.google.genai.tts.speaker-voice-configs[0].voice-name=Kore",
					"spring.ai.google.genai.tts.speaker-voice-configs[1].speaker=Bob",
					"spring.ai.google.genai.tts.speaker-voice-configs[1].voice-name=Charon")
			.run(context -> {
				GoogleGenAiTextToSpeechProperties props = context.getBean(GoogleGenAiTextToSpeechProperties.class);
				List<SpeakerVoiceConfig> speakers = props.toOptions().getSpeakerVoiceConfigs();
				assertThat(speakers).containsExactly(new SpeakerVoiceConfig("Sam", "Kore"),
						new SpeakerVoiceConfig("Bob", "Charon"));
			});
	}

	@Test
	void defaultModelBinding() {
		this.contextRunner.run(context -> {
			GoogleGenAiTextToSpeechProperties props = context.getBean(GoogleGenAiTextToSpeechProperties.class);
			assertThat(props.toOptions().getModel()).isEqualTo(GoogleGenAiAudioSpeechOptions.DEFAULT_MODEL);
		});
	}

	@Test
	void multiSpeakerTurnsBinding() {
		this.contextRunner
			.withPropertyValues("spring.ai.google.genai.tts.multi-speaker-turns[0].speaker=Sam",
					"spring.ai.google.genai.tts.multi-speaker-turns[0].text=How's it going?",
					"spring.ai.google.genai.tts.multi-speaker-turns[1].speaker=Bob",
					"spring.ai.google.genai.tts.multi-speaker-turns[1].text=Not too bad.")
			.run(context -> {
				GoogleGenAiTextToSpeechProperties props = context.getBean(GoogleGenAiTextToSpeechProperties.class);
				List<MultiSpeakerTurn> turns = props.toOptions().getMultiSpeakerTurns();
				assertThat(turns).containsExactly(new MultiSpeakerTurn("Sam", "How's it going?"),
						new MultiSpeakerTurn("Bob", "Not too bad."));
			});
	}

	@Configuration
	@EnableConfigurationProperties({ GoogleGenAiTextToSpeechConnectionProperties.class,
			GoogleGenAiTextToSpeechProperties.class })
	static class PropertiesTestConfiguration {

	}

}
