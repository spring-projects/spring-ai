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

import java.util.List;

import com.google.genai.types.GenerateContentResponsePromptFeedback;
import org.jspecify.annotations.Nullable;

/**
 * The feedback Gemini gives on a prompt. When the prompt is blocked, the response has no
 * candidates and this feedback is the only place that says why.
 * <p>
 * The block reason keeps the value the API sent, so a value the SDK does not know yet is
 * not lost.
 *
 * @param blockReason the reason the prompt was blocked, for example {@code SAFETY}, or
 * {@code null} when it was not blocked
 * @param blockReasonMessage a readable description of the block reason
 * @param safetyRatings the safety ratings of the prompt
 * @author Hyunjoon Choi
 * @since 2.1.0
 */
public record GoogleGenAiPromptFeedback(@Nullable String blockReason, @Nullable String blockReasonMessage,
		List<GoogleGenAiSafetyRating> safetyRatings) {

	/**
	 * Creates a prompt feedback from the one the Google GenAI SDK returned.
	 * @param promptFeedback the SDK prompt feedback
	 * @return the prompt feedback
	 */
	public static GoogleGenAiPromptFeedback from(GenerateContentResponsePromptFeedback promptFeedback) {
		return new GoogleGenAiPromptFeedback(promptFeedback.blockReason().map(Object::toString).orElse(null),
				promptFeedback.blockReasonMessage().orElse(null),
				promptFeedback.safetyRatings()
					.map(ratings -> ratings.stream().map(GoogleGenAiSafetyRating::from).toList())
					.orElse(List.of()));
	}

}
