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

import com.microsoft.cognitiveservices.speech.OutputFormat;
import com.microsoft.cognitiveservices.speech.ProfanityOption;
import com.microsoft.cognitiveservices.speech.PropertyId;
import com.microsoft.cognitiveservices.speech.SpeechConfig;

import org.springframework.util.StringUtils;

/**
 * Applies the model's own option values to an SDK {@link SpeechConfig}.
 * <p>
 * Keeping this in one place is what lets the options themselves stay free of natively
 * backed SDK enumerations, so they remain safe to bind from properties and to serialize.
 * An unset option is never applied, leaving the SDK's own default in place.
 *
 * @author Spring AI Contributors
 */
final class SpeechConfigOptionsApplier {

	private SpeechConfigOptionsApplier() {
	}

	static void apply(SpeechConfig speechConfig, AzureSpeechAudioTranscriptionOptions options) {
		if (StringUtils.hasText(options.getLanguage())) {
			speechConfig.setSpeechRecognitionLanguage(options.getLanguage());
		}
		if (StringUtils.hasText(options.getRecognitionModel())) {
			speechConfig.setModel(options.getRecognitionModel());
		}
		if (StringUtils.hasText(options.getEndpointId())) {
			speechConfig.setEndpointId(options.getEndpointId());
		}
		if (options.getProfanity() != null) {
			speechConfig.setProfanity(toProfanityOption(options.getProfanity()));
		}
		if (options.getOutputFormat() != null) {
			speechConfig.setOutputFormat(toOutputFormat(options.getOutputFormat()));
		}
		if (options.isWordLevelTimestampsEnabled()) {
			speechConfig.requestWordLevelTimestamps();
		}
		if (options.getLanguageIdMode() != null) {
			speechConfig.setProperty(PropertyId.SpeechServiceConnection_LanguageIdMode,
					toLanguageIdMode(options.getLanguageIdMode()));
		}
	}

	static ProfanityOption toProfanityOption(AzureSpeechAudioTranscriptionOptions.Profanity profanity) {
		return switch (profanity) {
			case MASKED -> ProfanityOption.Masked;
			case REMOVED -> ProfanityOption.Removed;
			case RAW -> ProfanityOption.Raw;
		};
	}

	static OutputFormat toOutputFormat(AzureSpeechAudioTranscriptionOptions.OutputFormat outputFormat) {
		return switch (outputFormat) {
			case SIMPLE -> OutputFormat.Simple;
			case DETAILED -> OutputFormat.Detailed;
		};
	}

	static String toLanguageIdMode(AzureSpeechAudioTranscriptionOptions.LanguageIdMode mode) {
		return switch (mode) {
			case AT_START -> "AtStart";
			case CONTINUOUS -> "Continuous";
		};
	}

}
