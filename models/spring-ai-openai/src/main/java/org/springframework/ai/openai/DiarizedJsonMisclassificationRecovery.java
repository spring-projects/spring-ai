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
import com.openai.models.audio.transcriptions.Transcription;
import com.openai.models.audio.transcriptions.TranscriptionDiarized;
import com.openai.models.audio.transcriptions.TranscriptionDiarizedSegment;
import org.jspecify.annotations.Nullable;

/**
 * Recovers {@code diarized_json} transcription responses that the OpenAI Java SDK
 * (version 4.42.0) misclassifies as a plain {@link Transcription} instead of
 * {@link TranscriptionDiarized}.
 * <p>
 * The real API's {@code diarized_json} response omits the {@code duration} field that the
 * SDK's generated model treats as required, which makes every candidate in the SDK's
 * response-type union fail validation; when that happens the SDK falls back to stuffing
 * the entire raw JSON payload into {@link Transcription#text()} rather than throwing.
 * Left alone, that raw JSON blob would surface as the transcript text. This class detects
 * that exact fallback shape and manually recovers the clean transcript text, per-speaker
 * segments and usage from the raw JSON.
 * <p>
 * This is a workaround for a bug outside Spring AI's control -- see
 * <a href="https://github.com/openai/openai-java/issues/802">openai-java#802</a> -- and
 * is applied only when
 * {@link OpenAiAudioTranscriptionOptions#isDiarizedJsonWorkaroundEnabled()} is
 * {@code true} (the default). Disable it via
 * {@link OpenAiAudioTranscriptionOptions.Builder#diarizedJsonWorkaroundEnabled(boolean)}
 * if this heuristic ever misfires, or once the upstream bug is fixed.
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
	 * misclassified as a plain {@link Transcription}. Returns {@code null} when
	 * {@code candidate} doesn't match the known fallback shape (a JSON object with a
	 * top-level {@code text} string field), so callers can safely keep using the SDK's
	 * own text as-is.
	 */
	static @Nullable Recovered tryRecover(Transcription candidate) {
		String rawText = candidate.text();
		if (rawText == null || rawText.isBlank() || rawText.charAt(0) != '{') {
			return null;
		}

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

		List<TranscriptionDiarizedSegment> segments = new ArrayList<>();
		for (JsonNode segmentNode : root.path("segments")) {
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

		Map<String, Object> usage = root.hasNonNull("usage")
				? OBJECT_MAPPER.convertValue(root.get("usage"), new TypeReference<Map<String, Object>>() {
				}) : null;

		return new Recovered(text, segments, usage);
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
