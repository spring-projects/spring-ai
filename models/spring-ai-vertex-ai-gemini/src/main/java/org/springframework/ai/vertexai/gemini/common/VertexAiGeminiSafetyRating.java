/*
 * Copyright 2024-2025 the original author or authors.
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

package org.springframework.ai.vertexai.gemini.common;

/**
 * Represents a safety rating returned by the Vertex AI Gemini API for generated content.
 * Safety ratings indicate the probability and severity of harmful content in a specific
 * category.
 *
 * @author Mark Pollack
 * @since 1.1.1
 * @see VertexAiGeminiSafetySetting
 */
public record VertexAiGeminiSafetyRating(HarmCategory category, HarmProbability probability, boolean blocked,
		float probabilityScore, HarmSeverity severity, float severityScore) {

	/**
	 * Enum representing different categories of harmful content.
	 */
	public enum HarmCategory {

		HARM_CATEGORY_UNSPECIFIED, HARM_CATEGORY_HATE_SPEECH, HARM_CATEGORY_DANGEROUS_CONTENT, HARM_CATEGORY_HARASSMENT,
		HARM_CATEGORY_SEXUALLY_EXPLICIT, HARM_CATEGORY_CIVIC_INTEGRITY

	}

	/**
	 * Enum representing the probability levels of harmful content.
	 */
	public enum HarmProbability {

		HARM_PROBABILITY_UNSPECIFIED, NEGLIGIBLE, LOW, MEDIUM, HIGH

	}

	/**
	 * Enum representing the severity levels of harmful content.
	 */
	public enum HarmSeverity {

		HARM_SEVERITY_UNSPECIFIED, HARM_SEVERITY_NEGLIGIBLE, HARM_SEVERITY_LOW, HARM_SEVERITY_MEDIUM, HARM_SEVERITY_HIGH

	}

}
