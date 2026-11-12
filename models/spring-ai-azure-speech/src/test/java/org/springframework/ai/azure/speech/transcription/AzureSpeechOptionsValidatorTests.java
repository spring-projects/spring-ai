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

package org.springframework.ai.azure.speech.transcription;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.Test;

import org.springframework.ai.azure.speech.transcription.AzureSpeechAudioTranscriptionOptions.LanguageIdMode;
import org.springframework.util.unit.DataSize;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatNoException;

/**
 * Tests for {@link AzureSpeechOptionsValidator}, which rejects option combinations before
 * any SDK object is created.
 *
 * @author Spring AI Contributors
 */
class AzureSpeechOptionsValidatorTests {

	@Test
	void aFixedLanguageCannotBeCombinedWithCandidateLanguages() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> AzureSpeechOptionsValidator.validate(AzureSpeechAudioTranscriptionOptions.builder()
				.language("en-US")
				.candidateLanguages(List.of("en-US", "de-DE"))
				.build()))
			.withMessageContaining("mutually exclusive");
	}

	@Test
	void anExplicitlyEmptyCandidateListIsRejected() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> AzureSpeechOptionsValidator
				.validate(AzureSpeechAudioTranscriptionOptions.builder().candidateLanguages(List.of()).build()))
			.withMessageContaining("must not be empty");
	}

	@Test
	void blankAndDuplicateCandidatesAreRejected() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> AzureSpeechOptionsValidator.validate(
					AzureSpeechAudioTranscriptionOptions.builder().candidateLanguages(List.of("en-US", "  ")).build()))
			.withMessageContaining("blank entry");
		assertThatIllegalArgumentException().isThrownBy(() -> AzureSpeechOptionsValidator.validate(
				AzureSpeechAudioTranscriptionOptions.builder().candidateLanguages(List.of("en-US", "en-US")).build()))
			.withMessageContaining("duplicates");
	}

	@Test
	void theCandidateCeilingDependsOnTheIdentificationMode() {
		List<String> five = List.of("en-US", "de-DE", "fr-FR", "es-ES", "it-IT");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> AzureSpeechOptionsValidator
				.validate(AzureSpeechAudioTranscriptionOptions.builder().candidateLanguages(five).build()))
			.withMessageContaining("at most 4");
		assertThatNoException()
			.isThrownBy(() -> AzureSpeechOptionsValidator.validate(AzureSpeechAudioTranscriptionOptions.builder()
				.candidateLanguages(five)
				.languageIdMode(LanguageIdMode.CONTINUOUS)
				.build()));
	}

	@Test
	void anIdentificationModeWithoutCandidatesIsRejected() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> AzureSpeechOptionsValidator.validate(
					AzureSpeechAudioTranscriptionOptions.builder().languageIdMode(LanguageIdMode.AT_START).build()))
			.withMessageContaining("requires candidateLanguages");
	}

	@Test
	void customSpeechCannotBeCombinedWithLanguageIdentification() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> AzureSpeechOptionsValidator.validate(AzureSpeechAudioTranscriptionOptions.builder()
				.endpointId("deployment")
				.candidateLanguages(List.of("en-US"))
				.build()))
			.withMessageContaining("cannot be combined");
	}

	@Test
	void aModelAndAnEndpointIdAreMutuallyExclusive() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> AzureSpeechOptionsValidator
				.validate(AzureSpeechAudioTranscriptionOptions.builder().model("m").endpointId("e").build()))
			.withMessageContaining("mutually exclusive");
	}

	@Test
	void nonPositiveTimeoutsAndSizesAreRejected() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> AzureSpeechOptionsValidator
				.validate(AzureSpeechAudioTranscriptionOptions.builder().timeout(Duration.ZERO).build()))
			.withMessageContaining("timeout must be positive");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> AzureSpeechOptionsValidator
				.validate(AzureSpeechAudioTranscriptionOptions.builder().timeout(Duration.ofSeconds(-1)).build()))
			.withMessageContaining("timeout must be positive");
		assertThatIllegalArgumentException()
			.isThrownBy(() -> AzureSpeechOptionsValidator
				.validate(AzureSpeechAudioTranscriptionOptions.builder().maxAudioSize(DataSize.ofBytes(0)).build()))
			.withMessageContaining("maxAudioSize must be positive");
	}

	@Test
	void blankPhrasesAreRejected() {
		assertThatIllegalArgumentException()
			.isThrownBy(() -> AzureSpeechOptionsValidator
				.validate(AzureSpeechAudioTranscriptionOptions.builder().phrases(List.of("Contoso", " ")).build()))
			.withMessageContaining("phrases must not contain a blank entry");
	}

	@Test
	void anOrdinaryCombinationPasses() {
		assertThatNoException()
			.isThrownBy(() -> AzureSpeechOptionsValidator.validate(AzureSpeechAudioTranscriptionOptions.builder()
				.language("en-US")
				.phrases(List.of("Contoso"))
				.timeout(Duration.ofSeconds(30))
				.build()));
	}

	@Test
	void noMessageCarriesACredential() {
		String message = catchMessage(() -> AzureSpeechOptionsValidator
			.validate(AzureSpeechAudioTranscriptionOptions.builder().model("m").endpointId("e").build()));

		assertThat(message).doesNotContain("api-key").doesNotContain("Bearer");
	}

	private static String catchMessage(Runnable runnable) {
		try {
			runnable.run();
			throw new AssertionError("expected a validation failure");
		}
		catch (IllegalArgumentException ex) {
			return String.valueOf(ex.getMessage());
		}
	}

}
