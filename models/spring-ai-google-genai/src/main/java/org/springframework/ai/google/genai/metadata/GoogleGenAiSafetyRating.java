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

package org.springframework.ai.google.genai.metadata;

import com.google.genai.types.SafetyRating;
import org.jspecify.annotations.Nullable;

/**
 * A safety rating that Gemini gives a candidate or a prompt for one harm category.
 * <p>
 * The category, probability and severity keep the values the API sent, so a value the SDK
 * does not know yet is not lost.
 *
 * @param category the harm category, for example {@code HARM_CATEGORY_HARASSMENT}
 * @param probability the probability of harm, for example {@code LOW}
 * @param severity the severity of harm, for example {@code HARM_SEVERITY_LOW}
 * @param blocked whether the content was blocked because of this rating
 * @param probabilityScore the probability score of harm
 * @param severityScore the severity score of harm
 * @author Hyunjoon Choi
 * @since 2.1.0
 */
public record GoogleGenAiSafetyRating(@Nullable String category, @Nullable String probability,
		@Nullable String severity, @Nullable Boolean blocked, @Nullable Float probabilityScore,
		@Nullable Float severityScore) {

	/**
	 * Creates a safety rating from the one the Google GenAI SDK returned.
	 * @param safetyRating the SDK safety rating
	 * @return the safety rating
	 */
	public static GoogleGenAiSafetyRating from(SafetyRating safetyRating) {
		return new GoogleGenAiSafetyRating(safetyRating.category().map(Object::toString).orElse(null),
				safetyRating.probability().map(Object::toString).orElse(null),
				safetyRating.severity().map(Object::toString).orElse(null), safetyRating.blocked().orElse(null),
				safetyRating.probabilityScore().orElse(null), safetyRating.severityScore().orElse(null));
	}

}
