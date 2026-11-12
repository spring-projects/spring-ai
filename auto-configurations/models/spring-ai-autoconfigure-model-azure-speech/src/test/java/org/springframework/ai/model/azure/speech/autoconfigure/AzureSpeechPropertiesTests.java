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

package org.springframework.ai.model.azure.speech.autoconfigure;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.azure.speech.transcription.AzureSpeechAudioTranscriptionOptions;
import org.springframework.ai.azure.speech.transcription.AzureSpeechAudioTranscriptionOptions.LanguageIdMode;
import org.springframework.ai.azure.speech.transcription.AzureSpeechAudioTranscriptionOptions.OutputFormat;
import org.springframework.ai.azure.speech.transcription.AzureSpeechAudioTranscriptionOptions.Profanity;
import org.springframework.ai.azure.speech.transcription.AzureSpeechConnectionDetails;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Binding tests for the Azure AI Speech connection and transcription properties (AC8),
 * including the redaction of the API key (AC14).
 *
 * @author Spring AI Contributors
 */
class AzureSpeechPropertiesTests {

	private static final String API_KEY = "super-secret-speech-key";

	private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
		.withConfiguration(AutoConfigurations.of(AzureSpeechTranscriptionAutoConfiguration.class))
		.withPropertyValues("spring.ai.model.audio.transcription=azure-speech");

	@Test
	void bindsConnectionPropertiesForARegion() {
		this.contextRunner
			.withPropertyValues("spring.ai.azure.speech.api-key=" + API_KEY, "spring.ai.azure.speech.region=westeurope")
			.run(context -> {
				AzureSpeechConnectionProperties properties = context.getBean(AzureSpeechConnectionProperties.class);
				assertThat(properties.getApiKey()).isEqualTo(API_KEY);
				assertThat(properties.getRegion()).isEqualTo("westeurope");
				assertThat(properties.getEndpoint()).isNull();

				AzureSpeechConnectionDetails details = properties.toConnectionDetails();
				assertThat(details.getApiKey()).isEqualTo(API_KEY);
				assertThat(details.getRegion()).isEqualTo("westeurope");
				assertThat(details.getEndpoint()).isNull();
			});
	}

	@Test
	void bindsConnectionPropertiesForAnEndpoint() {
		this.contextRunner
			.withPropertyValues("spring.ai.azure.speech.api-key=" + API_KEY,
					"spring.ai.azure.speech.endpoint=https://westeurope.api.cognitive.microsoft.com/")
			.run(context -> {
				AzureSpeechConnectionProperties properties = context.getBean(AzureSpeechConnectionProperties.class);
				assertThat(properties.getEndpoint()).isEqualTo("https://westeurope.api.cognitive.microsoft.com/");
				assertThat(properties.toConnectionDetails().getEndpoint())
					.hasToString("https://westeurope.api.cognitive.microsoft.com/");
			});
	}

	@Test
	void bindsEveryTranscriptionProperty() {
		this.contextRunner
			.withPropertyValues("spring.ai.azure.speech.api-key=" + API_KEY, "spring.ai.azure.speech.region=westeurope",
					"spring.ai.azure.speech.transcription.model=whisper-large",
					"spring.ai.azure.speech.transcription.language=en-US",
					"spring.ai.azure.speech.transcription.profanity=removed",
					"spring.ai.azure.speech.transcription.output-format=detailed",
					"spring.ai.azure.speech.transcription.word-level-timestamps=true",
					"spring.ai.azure.speech.transcription.phrases[0]=Spring AI",
					"spring.ai.azure.speech.transcription.phrases[1]=Azure Speech",
					"spring.ai.azure.speech.transcription.timeout=45s",
					"spring.ai.azure.speech.transcription.max-audio-size=25MB")
			.run(context -> {
				AzureSpeechTranscriptionProperties properties = context
					.getBean(AzureSpeechTranscriptionProperties.class);
				assertThat(properties.getModel()).isEqualTo("whisper-large");
				assertThat(properties.getLanguage()).isEqualTo("en-US");
				assertThat(properties.getProfanity()).isEqualTo(Profanity.REMOVED);
				assertThat(properties.getOutputFormat()).isEqualTo(OutputFormat.DETAILED);
				assertThat(properties.getWordLevelTimestamps()).isTrue();
				assertThat(properties.getPhrases()).containsExactly("Spring AI", "Azure Speech");
				assertThat(properties.getTimeout()).isEqualTo(Duration.ofSeconds(45));
				assertThat(properties.getMaxAudioSize()).isEqualTo(DataSize.ofMegabytes(25));

				assertThat(properties.toOptions()).isEqualTo(AzureSpeechAudioTranscriptionOptions.builder()
					.model("whisper-large")
					.language("en-US")
					.profanity(Profanity.REMOVED)
					.outputFormat(OutputFormat.DETAILED)
					.wordLevelTimestamps(true)
					.phrases(List.of("Spring AI", "Azure Speech"))
					.timeout(Duration.ofSeconds(45))
					.maxAudioSize(DataSize.ofMegabytes(25))
					.build());
			});
	}

	@Test
	void bindsTheLanguageIdentificationProperties() {
		this.contextRunner
			.withPropertyValues("spring.ai.azure.speech.api-key=" + API_KEY,
					"spring.ai.azure.speech.endpoint=https://westeurope.api.cognitive.microsoft.com/",
					"spring.ai.azure.speech.transcription.candidate-languages[0]=en-US",
					"spring.ai.azure.speech.transcription.candidate-languages[1]=de-DE",
					"spring.ai.azure.speech.transcription.language-id-mode=continuous")
			.run(context -> {
				AzureSpeechTranscriptionProperties properties = context
					.getBean(AzureSpeechTranscriptionProperties.class);
				assertThat(properties.getCandidateLanguages()).containsExactly("en-US", "de-DE");
				assertThat(properties.getLanguageIdMode()).isEqualTo(LanguageIdMode.CONTINUOUS);
				assertThat(properties.toOptions().isLanguageIdentificationEnabled()).isTrue();
			});
	}

	@Test
	void bindsTheCustomSpeechEndpointId() {
		this.contextRunner
			.withPropertyValues("spring.ai.azure.speech.api-key=" + API_KEY, "spring.ai.azure.speech.region=westeurope",
					"spring.ai.azure.speech.transcription.endpoint-id=a-custom-deployment")
			.run(context -> {
				AzureSpeechTranscriptionProperties properties = context
					.getBean(AzureSpeechTranscriptionProperties.class);
				assertThat(properties.getEndpointId()).isEqualTo("a-custom-deployment");
				assertThat(properties.toOptions().getModel()).isEqualTo("a-custom-deployment");
			});
	}

	@Test
	void unsetTranscriptionPropertiesLeaveTheModelDefaultsInPlace() {
		this.contextRunner
			.withPropertyValues("spring.ai.azure.speech.api-key=" + API_KEY, "spring.ai.azure.speech.region=westeurope")
			.run(context -> {
				AzureSpeechAudioTranscriptionOptions options = context.getBean(AzureSpeechTranscriptionProperties.class)
					.toOptions();
				assertThat(options).isEqualTo(AzureSpeechAudioTranscriptionOptions.builder().build());
				assertThat(options.resolvedTimeout()).isEqualTo(AzureSpeechAudioTranscriptionOptions.DEFAULT_TIMEOUT);
				assertThat(options.resolvedMaxAudioSize())
					.isEqualTo(AzureSpeechAudioTranscriptionOptions.DEFAULT_MAX_AUDIO_SIZE);
				assertThat(options.getModel()).isEqualTo(AzureSpeechAudioTranscriptionOptions.DEFAULT_MODEL);
			});
	}

	@Test
	void connectionPropertiesToStringRedactsTheKeyAndTheEndpoint() {
		AzureSpeechConnectionProperties properties = new AzureSpeechConnectionProperties();
		properties.setApiKey(API_KEY);
		properties.setEndpoint("https://westeurope.api.cognitive.microsoft.com/?key=" + API_KEY);

		assertThat(properties)
			.hasToString("AzureSpeechConnectionProperties{apiKey=[REDACTED], region=null, " + "endpoint=[SET]}");
		assertThat(properties.toString()).doesNotContain(API_KEY);
	}

	@Test
	void connectionPropertyFailuresNameThePropertyWithoutItsValue() {
		AzureSpeechConnectionProperties noKey = new AzureSpeechConnectionProperties();
		noKey.setRegion("westeurope");
		assertThatIllegalArgumentException().isThrownBy(noKey::toConnectionDetails)
			.withMessageContaining("spring.ai.azure.speech.api-key");

		AzureSpeechConnectionProperties badEndpoint = new AzureSpeechConnectionProperties();
		badEndpoint.setApiKey(API_KEY);
		badEndpoint.setEndpoint("not a uri " + API_KEY);
		assertThatThrownBy(badEndpoint::toConnectionDetails).isInstanceOf(IllegalArgumentException.class)
			.hasMessageContaining("spring.ai.azure.speech.endpoint")
			.hasMessageNotContaining(API_KEY);
	}

}
