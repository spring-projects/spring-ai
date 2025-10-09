/*
 * Copyright 2023-2025 the original author or authors.
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

package org.springframework.ai.google.genai.metadata;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.google.genai.types.MediaModality;
import com.google.genai.types.ModalityTokenCount;

/**
 * Represents token count information for a specific modality (text, image, audio, video).
 *
 * @author Dan Dobrin
 * @since 1.1.0
 */
public class GoogleGenAiModalityTokenCount {

	private final String modality;

	private final Integer tokenCount;

	/**
	 * Creates a new modality token count instance.
	 * @param modality the modality type (e.g., "TEXT", "IMAGE", "AUDIO", "VIDEO")
	 * @param tokenCount the number of tokens for this modality
	 */
	public GoogleGenAiModalityTokenCount(String modality, Integer tokenCount) {
		this.modality = modality;
		this.tokenCount = tokenCount;
	}

	/**
	 * Creates a GoogleGenAiModalityTokenCount from the SDK's ModalityTokenCount.
	 * @param modalityTokenCount the SDK modality token count
	 * @return a new GoogleGenAiModalityTokenCount instance
	 */
	public static GoogleGenAiModalityTokenCount from(ModalityTokenCount modalityTokenCount) {
		if (modalityTokenCount == null) {
			return null;
		}

		String modalityStr = modalityTokenCount.modality()
			.map(GoogleGenAiModalityTokenCount::convertModality)
			.orElse("UNKNOWN");

		Integer tokens = modalityTokenCount.tokenCount().orElse(0);

		return new GoogleGenAiModalityTokenCount(modalityStr, tokens);
	}

	private static String convertModality(MediaModality modality) {
		if (modality == null) {
			return "UNKNOWN";
		}

		// MediaModality returns its string value via toString()
		String modalityStr = modality.toString().toUpperCase();

		// Map SDK values to cleaner names
		return switch (modalityStr) {
			case "TEXT", "IMAGE", "VIDEO", "AUDIO", "DOCUMENT" -> modalityStr;
			case "MODALITY_UNSPECIFIED", "MEDIA_MODALITY_UNSPECIFIED" -> "UNKNOWN";
			default -> modalityStr;
		};
	}

	/**
	 * Returns the modality type.
	 * @return the modality type as a string
	 */
	@JsonProperty("modality")
	public String getModality() {
		return this.modality;
	}

	/**
	 * Returns the token count for this modality.
	 * @return the token count
	 */
	@JsonProperty("tokenCount")
	public Integer getTokenCount() {
		return this.tokenCount;
	}

	@Override
	public String toString() {
		return "GoogleGenAiModalityTokenCount{" + "modality='" + this.modality + '\'' + ", tokenCount="
				+ this.tokenCount + '}';
	}

}
