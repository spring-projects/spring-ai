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

import java.math.BigInteger;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

import org.jspecify.annotations.Nullable;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Converts the values a recognition result carries into the model's own types.
 * <p>
 * Everything here is a pure function of plain values, so it is unit-tested without the
 * SDK, its native libraries, or a service connection.
 *
 * @author Spring AI Contributors
 */
final class AzureSpeechResultMapper {

	/**
	 * Recognition offsets and durations are expressed in 100-nanosecond ticks.
	 */
	private static final long NANOS_PER_TICK = 100L;

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private AzureSpeechResultMapper() {
	}

	/**
	 * Converts a 100-nanosecond tick count to a {@link Duration}, treating a missing or
	 * negative value as zero.
	 */
	static Duration toDuration(@Nullable BigInteger ticks) {
		if (ticks == null || ticks.signum() <= 0) {
			return Duration.ZERO;
		}
		return Duration.ofNanos(ticks.longValueExact() * NANOS_PER_TICK);
	}

	/**
	 * Reduces the plain values one recognition result carries to a segment, applying the
	 * rule that an optional feature which was not requested yields an absent or empty
	 * field rather than a fabricated one.
	 * <p>
	 * The three flags are the resolved options the request ran with, so the only place
	 * that decides which optional fields a segment carries is this one function,
	 * reachable without the SDK. {@code detectedLanguage} is consulted only when language
	 * identification ran, because reading a detected language off a result that was
	 * recognized without it is meaningless.
	 * @param text the recognized text, exactly as the service returned it
	 * @param resultId the service-assigned result identifier
	 * @param offset the segment's start, measured from the beginning of the audio
	 * @param duration how long the segment spans
	 * @param detailedJson the raw JSON result the service attached, possibly {@code null}
	 * @param detectedLanguage supplies the detected language, invoked only when language
	 * identification is enabled
	 * @param languageIdentification whether automatic language identification ran
	 * @param wordTimestamps whether word-level timestamps were requested
	 * @param detailed whether detailed output was requested
	 */
	static AzureSpeechSegment toSegment(String text, String resultId, Duration offset, Duration duration,
			@Nullable String detailedJson, Supplier<@Nullable String> detectedLanguage, boolean languageIdentification,
			boolean wordTimestamps, boolean detailed) {
		return new AzureSpeechSegment(text, resultId, offset, duration,
				languageIdentification ? detectedLanguage.get() : null,
				wordTimestamps ? parseWords(detailedJson) : List.of(), detailed ? detailedJson : null);
	}

	/**
	 * Reads the word-level timings from a detailed recognition result.
	 * <p>
	 * The words live under the first {@code NBest} alternative. A result that carries
	 * none — which is what a simple-format response does — yields an empty list rather
	 * than a fabricated one.
	 * @param detailedJson the raw JSON result, possibly {@code null} or unparseable
	 * @return the words in order, or an empty list
	 */
	static List<AzureSpeechWord> parseWords(@Nullable String detailedJson) {
		if (detailedJson == null || detailedJson.isBlank()) {
			return List.of();
		}
		JsonNode root;
		try {
			root = OBJECT_MAPPER.readTree(detailedJson);
		}
		catch (RuntimeException ex) {
			// The service's own payload is not something the model can repair, and its
			// text must not reach a caller; an absent feature yields an absent value.
			return List.of();
		}
		JsonNode nBest = root.path("NBest");
		if (!nBest.isArray() || nBest.isEmpty()) {
			return List.of();
		}
		JsonNode words = nBest.get(0).path("Words");
		if (!words.isArray()) {
			return List.of();
		}
		List<AzureSpeechWord> parsed = new ArrayList<>(words.size());
		for (JsonNode word : words) {
			String text = word.path("Word").asString("");
			if (text.isEmpty()) {
				continue;
			}
			parsed.add(new AzureSpeechWord(text, ticksToDuration(word.path("Offset").asLong(0L)),
					ticksToDuration(word.path("Duration").asLong(0L))));
		}
		return List.copyOf(parsed);
	}

	private static Duration ticksToDuration(long ticks) {
		return (ticks <= 0) ? Duration.ZERO : Duration.ofNanos(ticks * NANOS_PER_TICK);
	}

}
