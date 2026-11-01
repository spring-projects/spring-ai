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

package org.springframework.ai.openai;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.openai.core.JsonValue;
import com.openai.models.audio.transcriptions.Transcription;
import com.openai.models.audio.transcriptions.TranscriptionDiarized;
import com.openai.models.audio.transcriptions.TranscriptionDiarizedSegment;
import org.jspecify.annotations.Nullable;

/**
 * Recovers {@code diarized_json} transcription responses that the OpenAI Java SDK
 * misclassifies as a plain {@link Transcription} instead of
 * {@link TranscriptionDiarized}.
 * <p>
 * Two fallback shapes are handled, both caused by the SDK bug described in
 * <a href="https://github.com/openai/openai-java/issues/802">openai-java#802</a>:
 * <ol>
 * <li><b>Raw-JSON-in-text</b> (SDK ≤ 4.42): the SDK stuffs the entire raw JSON payload
 * into {@link Transcription#text()} when no union candidate passes {@code validate()}.
 * This class parses the blob and recovers the clean text, segments, and usage.</li>
 * <li><b>Clean-text-with-additional-properties</b> (SDK 4.80): when the diarized_json
 * response has no segments (e.g. single-speaker short audio), the validity scores of
 * {@code TranscriptionDiarized} and {@code Transcription} tie; the SDK picks
 * {@code Transcription}, storing the clean transcript in {@code text()} and the
 * diarized-specific fields ({@code segments}, {@code task}) in
 * {@link Transcription#_additionalProperties()}. This class reads segments from
 * there.</li>
 * </ol>
 * <p>
 * Applied only when
 * {@link OpenAiAudioTranscriptionOptions#isDiarizedJsonWorkaroundEnabled()} is
 * {@code true} (the default). Disable via
 * {@link OpenAiAudioTranscriptionOptions.Builder#diarizedJsonWorkaroundEnabled(boolean)}
 * if this heuristic ever misfires, or once the upstream bug is fully fixed.
 *
 * @author Christian Tzolov
 * @deprecated This class is a workaround for a bug in the OpenAI Java SDK (see
 * openai-java#802) and will be removed once the upstream bug is fixed.
 */
@Deprecated
final class DiarizedJsonMisclassificationRecovery {

	private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

	private DiarizedJsonMisclassificationRecovery() {
	}

	/**
	 * Attempts to recover a {@code diarized_json} response that the OpenAI SDK
	 * misclassified as a plain {@link Transcription}. Two shapes are recognised:
	 * <ul>
	 * <li>The entire JSON body is in {@code text()} (old SDK ≤ 4.42 fallback): parsed and
	 * recovered in full.</li>
	 * <li>Clean text is in {@code text()} but diarized fields are in
	 * {@link Transcription#_additionalProperties()} (SDK 4.80 tied-validity fallback):
	 * segments are read from there.</li>
	 * </ul>
	 * Returns {@code null} when {@code candidate} matches neither shape.
	 */
	static @Nullable Recovered tryRecover(Transcription candidate) {
		String rawText = candidate.text();
		if (rawText == null || rawText.isBlank()) {
			return null;
		}

		// Shape 1: old SDK ≤ 4.42 — the entire raw JSON blob is in text().
		if (rawText.charAt(0) == '{') {
			return tryRecoverFromRawJsonInText(rawText);
		}

		// Shape 2: SDK 4.80 tied-validity — clean text is in text(), but segments and
		// other diarized fields landed in _additionalProperties() because Transcription
		// won the tie-break and it has no segments field.
		return tryRecoverFromAdditionalProperties(rawText, candidate);
	}

	private static @Nullable Recovered tryRecoverFromRawJsonInText(String rawText) {
		JsonNode root;
		try {
			root = OBJECT_MAPPER.readTree(rawText);
		}
		catch (Exception ex) {
			return null;
		}

		if (!root.isObject() || !root.path("text").isTextual()) {
			return null;
		}

		String text = root.path("text").asText();
		List<TranscriptionDiarizedSegment> segments = parseSegments(root.path("segments"));
		Map<String, Object> usage = root.hasNonNull("usage")
				? OBJECT_MAPPER.convertValue(root.get("usage"), new TypeReference<Map<String, Object>>() {
				}) : null;

		return new Recovered(text, segments, usage);
	}

	private static @Nullable Recovered tryRecoverFromAdditionalProperties(String text, Transcription candidate) {
		Map<String, JsonValue> additionalProperties = candidate._additionalProperties();
		// Only recover when "segments" is present as an additional property —
		// that is the distinguishing marker of a misclassified diarized_json response.
		JsonValue segmentsValue = additionalProperties.get("segments");
		if (segmentsValue == null) {
			return null;
		}

		JsonNode segmentsNode;
		try {
			segmentsNode = segmentsValue.convert(JsonNode.class);
		}
		catch (Exception ex) {
			return null;
		}
		if (segmentsNode == null) {
			return null;
		}

		List<TranscriptionDiarizedSegment> segments = parseSegments(segmentsNode);

		// Usage may have been deserialized into Transcription.usage() if the structure
		// matched, or may remain in additionalProperties if it didn't. Try both.
		Map<String, Object> usage = null;
		JsonValue usageValue = additionalProperties.get("usage");
		if (usageValue != null) {
			try {
				Map<String, Object> converted = usageValue.convert(new TypeReference<Map<String, Object>>() {
				});
				if (converted != null) {
					usage = converted;
				}
			}
			catch (Exception ex) {
				// ignore — fall through to the typed accessor below
			}
		}
		if (usage == null) {
			try {
				usage = candidate.usage()
					.map(u -> OBJECT_MAPPER.convertValue(u, new TypeReference<Map<String, Object>>() {
					}))
					.orElse(null);
			}
			catch (Exception ex) {
				// usage stays null
			}
		}

		return new Recovered(text, segments, usage);
	}

	private static List<TranscriptionDiarizedSegment> parseSegments(JsonNode segmentsNode) {
		List<TranscriptionDiarizedSegment> segments = new ArrayList<>();
		for (JsonNode segmentNode : segmentsNode) {
			if (!segmentNode.hasNonNull("id") || !segmentNode.hasNonNull("speaker") || !segmentNode.hasNonNull("start")
					|| !segmentNode.hasNonNull("end") || !segmentNode.hasNonNull("text")) {
				continue;
			}
			segments.add(TranscriptionDiarizedSegment.builder()
				.id(segmentNode.get("id").asText())
				.speaker(segmentNode.get("speaker").asText())
				.start((float) segmentNode.get("start").asDouble())
				.end((float) segmentNode.get("end").asDouble())
				.text(segmentNode.get("text").asText())
				.build());
		}
		return segments;
	}

	/**
	 * The clean text, speaker segments and usage recovered from a misclassified
	 * {@code diarized_json} response. {@code duration} isn't recoverable: the real API
	 * response doesn't include it, which is what triggers the SDK misclassification in
	 * the first place.
	 */
	record Recovered(String text, List<TranscriptionDiarizedSegment> segments, @Nullable Map<String, Object> usage) {
	}

}
