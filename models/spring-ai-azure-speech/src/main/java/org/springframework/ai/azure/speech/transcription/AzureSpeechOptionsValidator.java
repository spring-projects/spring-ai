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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.jspecify.annotations.Nullable;

import org.springframework.util.StringUtils;
import org.springframework.util.unit.DataSize;

/**
 * Rejects option combinations the Azure Speech SDK cannot honor, before any SDK object is
 * created and therefore before any connection is attempted.
 * <p>
 * Every message is built from the option values themselves, which never carry a
 * credential.
 *
 * @author Spring AI Contributors
 */
final class AzureSpeechOptionsValidator {

	/**
	 * Candidate-language ceilings documented for automatic language identification.
	 */
	private static final int MAX_CANDIDATES_AT_START = 4;

	private static final int MAX_CANDIDATES_CONTINUOUS = 10;

	private AzureSpeechOptionsValidator() {
	}

	static void validate(AzureSpeechAudioTranscriptionOptions options) {
		List<String> candidates = options.getCandidateLanguages();
		boolean hasCandidates = candidates != null && !candidates.isEmpty();

		if (candidates != null && candidates.isEmpty()) {
			throw new IllegalArgumentException(
					"candidateLanguages must not be empty; leave it unset to disable language identification");
		}
		if (candidates != null && hasCandidates) {
			validateCandidates(candidates, options.getLanguageIdMode());
		}
		if (StringUtils.hasText(options.getLanguage()) && hasCandidates) {
			throw new IllegalArgumentException(
					"language and candidateLanguages are mutually exclusive: set a fixed recognition language, "
							+ "or candidate languages for automatic language identification, not both");
		}
		if (options.getLanguageIdMode() != null && !hasCandidates) {
			throw new IllegalArgumentException("languageIdMode requires candidateLanguages");
		}
		if (StringUtils.hasText(options.getEndpointId()) && hasCandidates) {
			throw new IllegalArgumentException(
					"endpointId and candidateLanguages cannot be combined: Custom Speech with automatic language "
							+ "identification needs a per-language source language configuration, which is not "
							+ "supported yet");
		}
		if (StringUtils.hasText(options.getRecognitionModel()) && StringUtils.hasText(options.getEndpointId())) {
			throw new IllegalArgumentException(
					"model and endpointId are mutually exclusive: a Custom Speech deployment already selects a model");
		}
		validateTimeout(options.getTimeout());
		validateMaxAudioSize(options.getMaxAudioSize());
		validatePhrases(options.getPhrases());
	}

	private static void validateCandidates(List<String> candidates,
			AzureSpeechAudioTranscriptionOptions.@Nullable LanguageIdMode mode) {
		Set<String> seen = new HashSet<>();
		for (String candidate : candidates) {
			if (!StringUtils.hasText(candidate)) {
				throw new IllegalArgumentException("candidateLanguages must not contain a blank entry");
			}
			if (!seen.add(candidate)) {
				throw new IllegalArgumentException("candidateLanguages must not contain duplicates: " + candidate);
			}
		}
		int limit = (mode == AzureSpeechAudioTranscriptionOptions.LanguageIdMode.CONTINUOUS) ? MAX_CANDIDATES_CONTINUOUS
				: MAX_CANDIDATES_AT_START;
		if (candidates.size() > limit) {
			throw new IllegalArgumentException(
					"candidateLanguages allows at most " + limit + " entries for "
							+ ((mode == AzureSpeechAudioTranscriptionOptions.LanguageIdMode.CONTINUOUS) ? "continuous"
									: "at-start")
							+ " language identification, but " + candidates.size() + " were given");
		}
	}

	private static void validateTimeout(@Nullable Duration timeout) {
		if (timeout != null && (timeout.isZero() || timeout.isNegative())) {
			throw new IllegalArgumentException("timeout must be positive, but was " + timeout);
		}
	}

	private static void validateMaxAudioSize(@Nullable DataSize maxAudioSize) {
		if (maxAudioSize != null && maxAudioSize.toBytes() <= 0) {
			throw new IllegalArgumentException("maxAudioSize must be positive, but was " + maxAudioSize);
		}
	}

	private static void validatePhrases(@Nullable List<String> phrases) {
		if (phrases == null) {
			return;
		}
		for (String phrase : phrases) {
			if (!StringUtils.hasText(phrase)) {
				throw new IllegalArgumentException("phrases must not contain a blank entry");
			}
		}
	}

}
